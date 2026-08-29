package com.saad.autocaption;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class AudioExtractor {

    public interface ExtractCallback {
        void onSuccess(File wavFile);
        void onError(String message);
    }

    public static void extractAudioToWav(
            Context context,
            Uri videoUri,
            ExtractCallback callback) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File outputFile = new File(
                            context.getCacheDir(),
                            "extracted_audio.wav");

                    extract(context, videoUri, outputFile);

                    callback.onSuccess(outputFile);

                } catch (Exception e) {
                    callback.onError(
                            "Audio extraction failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void extract(
            Context context,
            Uri videoUri,
            File outputFile) throws Exception {

        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, videoUri, null);

        int audioTrackIndex = -1;
        MediaFormat audioFormat = null;

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);

            if (mime != null && mime.startsWith("audio/")) {
                audioTrackIndex = i;
                audioFormat = format;
                break;
            }
        }

        if (audioTrackIndex == -1) {
            throw new Exception("No audio track found in video");
        }

        extractor.selectTrack(audioTrackIndex);

        String mime = audioFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(audioFormat, null, null, 0);
        decoder.start();

        // These are just fallback/initial guesses from the *input* format.
        // The real values come from the decoder's OUTPUT format below,
        // which is what the PCM data we write actually is.
        int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        boolean gotOutputFormat = false;

        File tempPcm = new File(context.getCacheDir(), "temp_pcm.raw");
        FileOutputStream pcmOutput = new FileOutputStream(tempPcm);

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        while (!sawOutputEOS) {

            if (!sawInputEOS) {
                int inputBufferIndex = decoder.dequeueInputBuffer(10000);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer =
                            decoder.getInputBuffer(inputBufferIndex);

                    int sampleSize = extractor.readSampleData(inputBuffer, 0);

                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        long presentationTime = extractor.getSampleTime();
                        decoder.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                presentationTime, 0);
                        extractor.advance();
                    }
                }
            }

            int outputBufferIndex =
                    decoder.dequeueOutputBuffer(bufferInfo, 10000);

            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // THIS is the real, authoritative format of the PCM data
                // the decoder will actually produce. Input format's
                // declared sample rate/channels can differ from this.
                MediaFormat outputFormat = decoder.getOutputFormat();
                sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                gotOutputFormat = true;

            } else if (outputBufferIndex >= 0) {

                ByteBuffer outputBuffer =
                        decoder.getOutputBuffer(outputBufferIndex);

                byte[] data = new byte[bufferInfo.size];
                outputBuffer.get(data);
                pcmOutput.write(data);

                decoder.releaseOutputBuffer(outputBufferIndex, false);

                if ((bufferInfo.flags &
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            }
        }

        pcmOutput.close();
        decoder.stop();
        decoder.release();
        extractor.release();

        if (!gotOutputFormat) {
            // Some devices/codecs never fire the format-changed event if
            // output already matches input — in that case the input
            // format's values were correct all along, so we proceed.
        }

        writeWavFile(tempPcm, outputFile, sampleRate, channelCount);
    }

    private static void writeWavFile(
            File pcmFile,
            File wavFile,
            int sampleRate,
            int channels) throws Exception {

        java.io.FileInputStream in = new java.io.FileInputStream(pcmFile);

        long pcmSize;
        int outputChannels = 1; // we always output mono

        if (channels == 2) {
            File monoFile = new File(pcmFile.getParentFile(), "temp_pcm_mono.raw");
            FileOutputStream monoOut = new FileOutputStream(monoFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            byte[] leftover = null;

            while ((bytesRead = in.read(buffer)) != -1) {
                byte[] chunk = buffer;
                int len = bytesRead;

                if (leftover != null) {
                    byte[] combined = new byte[leftover.length + len];
                    System.arraycopy(leftover, 0, combined, 0, leftover.length);
                    System.arraycopy(buffer, 0, combined, leftover.length, len);
                    chunk = combined;
                    len = combined.length;
                    leftover = null;
                }

                int usableLen = len - (len % 4);
                if (usableLen < len) {
                    leftover = new byte[len - usableLen];
                    System.arraycopy(chunk, usableLen, leftover, 0, leftover.length);
                }

                byte[] monoChunk = new byte[usableLen / 2];
                int monoIndex = 0;

                for (int i = 0; i < usableLen; i += 4) {
                    short left = (short) ((chunk[i] & 0xff) | (chunk[i + 1] << 8));
                    short right = (short) ((chunk[i + 2] & 0xff) | (chunk[i + 3] << 8));
                    short mono = (short) ((left + right) / 2);

                    monoChunk[monoIndex++] = (byte) (mono & 0xff);
                    monoChunk[monoIndex++] = (byte) ((mono >> 8) & 0xff);
                }

                monoOut.write(monoChunk, 0, monoIndex);
            }

            monoOut.close();
            in.close();
            pcmFile.delete();

            in = new java.io.FileInputStream(monoFile);
            pcmSize = monoFile.length();

            writeHeaderAndCopy(in, wavFile, sampleRate, outputChannels, pcmSize);
            monoFile.delete();

        } else {
            pcmSize = pcmFile.length();
            writeHeaderAndCopy(in, wavFile, sampleRate, outputChannels, pcmSize);
            pcmFile.delete();
        }
    }

    private static void writeHeaderAndCopy(
            java.io.FileInputStream in,
            File wavFile,
            int sampleRate,
            int channels,
            long pcmSize) throws Exception {

        long totalSize = pcmSize + 36;

        FileOutputStream out = new FileOutputStream(wavFile);

        int byteRate = sampleRate * channels * 2;

        byte[] header = new byte[44];

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalSize & 0xff);
        header[5] = (byte) ((totalSize >> 8) & 0xff);
        header[6] = (byte) ((totalSize >> 16) & 0xff);
        header[7] = (byte) ((totalSize >> 24) & 0xff);
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
        header[40] = (byte) (pcmSize & 0xff);
        header[41] = (byte) ((pcmSize >> 8) & 0xff);
        header[42] = (byte) ((pcmSize >> 16) & 0xff);
        header[43] = (byte) ((pcmSize >> 24) & 0xff);

        out.write(header);

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }

        in.close();
        out.close();
    }
}
