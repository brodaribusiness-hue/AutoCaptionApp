package com.saad.autocaption;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                String uniqueId = UUID.randomUUID().toString();
                File outputFile = new File(
                        context.getCacheDir(),
                        "extracted_audio_" + uniqueId + ".wav");
                File tempPcm = new File(
                        context.getCacheDir(),
                        "temp_pcm_" + uniqueId + ".raw");

                try {
                    extract(context, videoUri, outputFile, tempPcm);
                    callback.onSuccess(outputFile);
                } catch (Exception e) {
                    outputFile.delete();
                    String message = e.getMessage() != null ? e.getMessage() : e.toString();
                    callback.onError("Audio extraction failed: " + message);
                } finally {
                    tempPcm.delete();
                }
            }
        }).start();
    }

    private static void extract(
            Context context,
            Uri videoUri,
            File outputFile,
            File tempPcm) throws Exception {

        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        FileOutputStream pcmOutput = null;

        try {
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
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(audioFormat, null, null, 0);
            decoder.start();

            int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            pcmOutput = new FileOutputStream(tempPcm);

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;

            while (!sawOutputEOS) {

                if (!sawInputEOS) {
                    int inputBufferIndex = decoder.dequeueInputBuffer(10000);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);

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

                int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000);

                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = decoder.getOutputFormat();
                    sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

                } else if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                    byte[] data = new byte[bufferInfo.size];
                    outputBuffer.get(data);
                    pcmOutput.write(data);
                    decoder.releaseOutputBuffer(outputBufferIndex, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                    }
                }
            }

            pcmOutput.close();
            pcmOutput = null;

            writeWavFile(tempPcm, outputFile, sampleRate, channelCount);

        } finally {
            if (pcmOutput != null) {
                try { pcmOutput.close(); } catch (Exception ignored) {}
            }
            if (decoder != null) {
                try { decoder.stop(); } catch (Exception ignored) {}
                try { decoder.release(); } catch (Exception ignored) {}
            }
            try { extractor.release(); } catch (Exception ignored) {}
        }
    }

    private static void writeWavFile(
            File pcmFile,
            File wavFile,
            int srcSampleRate,
            int srcChannels) throws Exception {

        int targetSampleRate = 16000;
        int targetChannels = 1;

        File processedPcm = new File(pcmFile.getParentFile(), pcmFile.getName() + ".16k.raw");

        try (FileInputStream in = new FileInputStream(pcmFile);
             FileOutputStream out = new FileOutputStream(processedPcm)) {

            byte[] inBuffer = new byte[4096];
            int bytesRead;
            double resampleRatio = (double) srcSampleRate / targetSampleRate;

            ByteArrayOutputStream rawStream = new ByteArrayOutputStream();
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
            FileInputStream in,
            File wavFile,
            int sampleRate,
            int channels,
            long pcmSize) throws Exception {

        long totalSize = pcmSize + 36;

        try (FileOutputStream out = new FileOutputStream(wavFile)) {
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
        }
    }
}
