package com.saad.autocaption;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

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

    public static void extractAudioToWav(
            Context context,
            Uri videoUri,
            ExtractCallback callback) {

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

                tempPcm = new File(context.getCacheDir(), "raw_audio_" + UUID.randomUUID() + ".pcm");
                wavFile = new File(context.getCacheDir(), "audio_16k_" + UUID.randomUUID() + ".wav");

                try (FileOutputStream pcmOut = new FileOutputStream(tempPcm)) {
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
                }

                // Resample PCM from srcSampleRate to strict 16000 Hz Mono for Vosk model
                write16kWavFile(tempPcm, wavFile, srcSampleRate, srcChannels);

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

    private static void write16kWavFile(
            File pcmFile, File wavFile, int srcSampleRate, int srcChannels) throws Exception {

        int targetSampleRate = 16000;
        int targetChannels = 1;
        File processedPcm = new File(pcmFile.getParentFile(), pcmFile.getName() + ".16k.raw");

        try (FileInputStream in = new FileInputStream(pcmFile);
             FileOutputStream out = new FileOutputStream(processedPcm)) {

            byte[] inBuffer = new byte[4096];
            int bytesRead;
            double resampleRatio = (double) srcSampleRate / targetSampleRate;

            java.io.ByteArrayOutputStream rawStream = new java.io.ByteArrayOutputStream();
            while ((bytesRead = in.read(inBuffer)) != -1) {
                rawStream.write(inBuffer, 0, bytesRead);
            }

            byte[] allPcm = rawStream.toByteArray();
            int totalSourceSamples = allPcm.length / (2 * srcChannels);
            int totalTargetSamples = (int) (totalSourceSamples / resampleRatio);

            byte[] outBuffer = new byte[totalTargetSamples * 2];
            int outOffset = 0;

            for (int i = 0; i < totalTargetSamples; i++) {
                int srcIndex = (int) (i * resampleRatio);
                if (srcIndex >= totalSourceSamples) break;

                int sampleOffset = srcIndex * 2 * srcChannels;
                short monoSample;

                if (srcChannels == 2 && (sampleOffset + 3) < allPcm.length) {
                    short left = (short) ((allPcm[sampleOffset] & 0xFF) | (allPcm[sampleOffset + 1] << 8));
                    short right = (short) ((allPcm[sampleOffset + 2] & 0xFF) | (allPcm[sampleOffset + 3] << 8));
                    monoSample = (short) ((left + right) / 2);
                } else if ((sampleOffset + 1) < allPcm.length) {
                    monoSample = (short) ((allPcm[sampleOffset] & 0xFF) | (allPcm[sampleOffset + 1] << 8));
                } else {
                    monoSample = 0;
                }

                outBuffer[outOffset++] = (byte) (monoSample & 0xFF);
                outBuffer[outOffset++] = (byte) ((monoSample >> 8) & 0xFF);
            }

            out.write(outBuffer, 0, outOffset);
        }

        try (FileInputStream finalIn = new FileInputStream(processedPcm)) {
            writeHeaderAndCopy(finalIn, wavFile, targetSampleRate, targetChannels, processedPcm.length());
        } finally {
            processedPcm.delete();
        }
    }

    private static void writeHeaderAndCopy(
            FileInputStream in, File wavFile, int sampleRate, int channels, long pcmDataLength) throws Exception {

        long totalDataLen = pcmDataLength + 36;
        long byteRate = sampleRate * channels * 2L;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 2); header[33] = 0;
        header[34] = 16; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (pcmDataLength & 0xff);
        header[41] = (byte) ((pcmDataLength >> 8) & 0xff);
        header[42] = (byte) ((pcmDataLength >> 16) & 0xff);
        header[43] = (byte) ((pcmDataLength >> 24) & 0xff);

        try (FileOutputStream out = new FileOutputStream(wavFile)) {
            out.write(header);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
