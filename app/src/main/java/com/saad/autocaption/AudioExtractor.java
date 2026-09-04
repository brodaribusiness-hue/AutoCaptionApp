package com.saad.autocaption;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

public class AudioExtractor {

    public interface ExtractCallback {
        void onSuccess(File wavFile);
        void onError(String message);
    }

    public static void extractAudioToWav(Context context, Uri videoUri, ExtractCallback callback) {
        new Thread(() -> {
            MediaExtractor extractor = new MediaExtractor();
            MediaCodec decoder = null;
            File tempPcm = null;
            File wavFile = null;

            try {
                extractor.setDataSource(context, videoUri, null);

                int audioTrackIndex = -1;
                MediaFormat format = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat f = extractor.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        audioTrackIndex = i;
                        format = f;
                        break;
                    }
                }

                if (audioTrackIndex == -1) {
                    callback.onError("No audio track found in video");
                    return;
                }

                extractor.selectTrack(audioTrackIndex);
                String mime = format.getString(MediaFormat.KEY_MIME);
                decoder = MediaCodec.createDecoderByType(mime);
                decoder.configure(format, null, null, 0);
                decoder.start();

                int srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

                String uid = UUID.randomUUID().toString();
                tempPcm = new File(context.getCacheDir(), "raw_" + uid + ".pcm");
                wavFile = new File(context.getCacheDir(), "audio_16k_" + uid + ".wav");

                try (BufferedOutputStream pcmOut = new BufferedOutputStream(new FileOutputStream(tempPcm))) {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    boolean sawInputEOS = false;
                    boolean sawOutputEOS = false;

                    while (!sawOutputEOS) {
                        if (!sawInputEOS) {
                            int inIndex = decoder.dequeueInputBuffer(10000);
                            if (inIndex >= 0) {
                                ByteBuffer inBuf = decoder.getInputBuffer(inIndex);
                                int sampleSize = extractor.readSampleData(inBuf, 0);
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    sawInputEOS = true;
                                } else {
                                    decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                                    extractor.advance();
                                }
                            }
                        }

                        int outIndex = decoder.dequeueOutputBuffer(info, 10000);
                        if (outIndex >= 0) {
                            ByteBuffer outBuf = decoder.getOutputBuffer(outIndex);
                            byte[] chunk = new byte[info.size];
                            outBuf.get(chunk);
                            outBuf.clear();
                            pcmOut.write(chunk);
                            decoder.releaseOutputBuffer(outIndex, false);

                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEOS = true;
                            }
                        }
                    }
                    pcmOut.flush();
                }

                // Chunk-by-chunk stream downsampling (Zero Memory Spike)
                resampleTo16kMonoWav(tempPcm, wavFile, srcSampleRate, srcChannels);

                File finalWav = wavFile;
                callback.onSuccess(finalWav);

            } catch (Exception e) {
                callback.onError("Audio extraction error: " + e.getMessage());
            } finally {
                if (decoder != null) {
                    try { decoder.stop(); decoder.release(); } catch (Exception ignored) {}
                }
                extractor.release();
                if (tempPcm != null && tempPcm.exists()) tempPcm.delete();
            }
        }).start();
    }

    private static void resampleTo16kMonoWav(File pcmFile, File wavFile, int srcSampleRate, int srcChannels) throws Exception {
        int targetSampleRate = 16000;
        int targetChannels = 1;
        File pcm16k = new File(pcmFile.getParentFile(), pcmFile.getName() + ".16k.raw");

        double ratio = (double) srcSampleRate / targetSampleRate;
        byte[] inChunk = new byte[8192 * srcChannels * 2];

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(pcmFile));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(pcm16k))) {

            int bytesRead;
            while ((bytesRead = in.read(inChunk)) != -1) {
                int samplesInChunk = bytesRead / (2 * srcChannels);
                int targetSamples = (int) (samplesInChunk / ratio);
                byte[] outChunk = new byte[targetSamples * 2];
                int outOffset = 0;

                for (int i = 0; i < targetSamples; i++) {
                    int srcIndex = (int) (i * ratio);
                    if (srcIndex >= samplesInChunk) break;

                    int offset = srcIndex * 2 * srcChannels;
                    short monoSample;

                    if (srcChannels >= 2 && (offset + 3) < bytesRead) {
                        short left = (short) ((inChunk[offset] & 0xFF) | (inChunk[offset + 1] << 8));
                        short right = (short) ((inChunk[offset + 2] & 0xFF) | (inChunk[offset + 3] << 8));
                        monoSample = (short) ((left + right) / 2);
                    } else if ((offset + 1) < bytesRead) {
                        monoSample = (short) ((inChunk[offset] & 0xFF) | (inChunk[offset + 1] << 8));
                    } else {
                        monoSample = 0;
                    }

                    outChunk[outOffset++] = (byte) (monoSample & 0xFF);
                    outChunk[outOffset++] = (byte) ((monoSample >> 8) & 0xFF);
                }
                out.write(outChunk, 0, outOffset);
            }
            out.flush();
        }

        try (BufferedInputStream finalIn = new BufferedInputStream(new FileInputStream(pcm16k))) {
            writeHeaderAndCopy(finalIn, wavFile, targetSampleRate, targetChannels, pcm16k.length());
        } finally {
            pcm16k.delete();
        }
    }

    private static void writeHeaderAndCopy(BufferedInputStream in, File wavFile, int sampleRate, int channels, long pcmLen) throws Exception {
        long totalDataLen = pcmLen + 36;
        long byteRate = sampleRate * channels * 2L;

        byte[] h = new byte[44];
        h[0] = 'R'; h[1] = 'I'; h[2] = 'F'; h[3] = 'F';
        h[4] = (byte) (totalDataLen & 0xff);
        h[5] = (byte) ((totalDataLen >> 8) & 0xff);
        h[6] = (byte) ((totalDataLen >> 16) & 0xff);
        h[7] = (byte) ((totalDataLen >> 24) & 0xff);
        h[8] = 'W'; h[9] = 'A'; h[10] = 'V'; h[11] = 'E';
        h[12] = 'f'; h[13] = 'm'; h[14] = 't'; h[15] = ' ';
        h[16] = 16; h[17] = 0; h[18] = 0; h[19] = 0;
        h[20] = 1; h[21] = 0;
        h[22] = (byte) channels; h[23] = 0;
        h[24] = (byte) (sampleRate & 0xff);
        h[25] = (byte) ((sampleRate >> 8) & 0xff);
        h[26] = (byte) ((sampleRate >> 16) & 0xff);
        h[27] = (byte) ((sampleRate >> 24) & 0xff);
        h[28] = (byte) (byteRate & 0xff);
        h[29] = (byte) ((byteRate >> 8) & 0xff);
        h[30] = (byte) ((byteRate >> 16) & 0xff);
        h[31] = (byte) ((byteRate >> 24) & 0xff);
        h[32] = (byte) (channels * 2); h[33] = 0;
        h[34] = 16; h[35] = 0;
        h[36] = 'd'; h[37] = 'a'; h[38] = 't'; h[39] = 'a';
        h[40] = (byte) (pcmLen & 0xff);
        h[41] = (byte) ((pcmLen >> 8) & 0xff);
        h[42] = (byte) ((pcmLen >> 16) & 0xff);
        h[43] = (byte) ((pcmLen >> 24) & 0xff);

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(wavFile))) {
            out.write(h);
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            out.flush();
        }
    }
}
