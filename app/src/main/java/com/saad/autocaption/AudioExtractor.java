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

        int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

        FileOutputStream pcmOutput = new FileOutputStream(
                new File(context.getCacheDir(), "temp_pcm.raw"));

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
