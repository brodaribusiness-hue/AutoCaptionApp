package com.saad.autocaption;

import android.os.Handler;
import android.os.Looper;

import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class SpeechToText {

    public interface ResultCallback {
        void onProgress(String message);
        void onSuccess(List<String> jsonResults);
        void onError(String message);
    }

    private static Model cachedModel;
    private static String cachedModelPath;
    private static int activeRecognitions = 0;
    private static boolean releasePending = false;

    public static void recognize(File modelDir, File wavFile, ResultCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            Recognizer recognizer = null;
            BufferedInputStream inputStream = null;

            synchronized (SpeechToText.class) {
                activeRecognitions++;
            }

            try {
                mainHandler.post(() -> callback.onProgress("Loading speech model..."));
                Model model = getOrLoadModel(modelDir);

                WavInfo wavInfo = readWavInfo(wavFile);
                float sampleRate = wavInfo.sampleRate;
                if (sampleRate < 8000 || sampleRate > 48000) {
                    sampleRate = 16000.0f;
                }

                recognizer = new Recognizer(model, sampleRate);
                recognizer.setWords(true);

                inputStream = new BufferedInputStream(new FileInputStream(wavFile));
                long skipped = inputStream.skip(wavInfo.dataOffset);
                if (skipped != wavInfo.dataOffset) {
                    throw new Exception("Could not seek to WAV audio data");
                }

                long totalAudioBytes = wavFile.length() - wavInfo.dataOffset;
                long bytesProcessed = 0;
                int lastReportedPercent = -1;

                // 16KB buffer for high recognition throughput and reduced JNI overhead
                byte[] buffer = new byte[16384];
                int bytesRead;
                List<String> jsonResults = new ArrayList<>();

                while ((bytesRead = inputStream.read(buffer)) > 0) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        jsonResults.add(recognizer.getResult());
                    }

                    bytesProcessed += bytesRead;
                    if (totalAudioBytes > 0) {
                        int percent = (int) (bytesProcessed * 100 / totalAudioBytes);
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent;
                            int finalPercent = percent;
                            mainHandler.post(() -> callback.onProgress("Recognizing speech... " + finalPercent + "%"));
                        }
                    }
                }

                String finalResult = recognizer.getFinalResult();
                if (finalResult != null && !finalResult.isEmpty()) {
                    jsonResults.add(finalResult);
                }

                mainHandler.post(() -> callback.onSuccess(jsonResults));

            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                mainHandler.post(() -> callback.onError("Speech recognition failed: " + message));
            } finally {
                if (inputStream != null) {
                    try { inputStream.close(); } catch (Exception ignored) {}
                }
                if (recognizer != null) {
                    recognizer.close();
                }
                synchronized (SpeechToText.class) {
                    activeRecognitions--;
                    if (activeRecognitions == 0 && releasePending) {
                        releasePending = false;
                        if (cachedModel != null) {
                            cachedModel.close();
                            cachedModel = null;
                            cachedModelPath = null;
                        }
                    }
                }
            }
        }).start();
    }

    private static synchronized Model getOrLoadModel(File modelDir) throws Exception {
        String path = modelDir.getAbsolutePath();
        if (cachedModel == null || !path.equals(cachedModelPath)) {
            if (cachedModel != null) {
                cachedModel.close();
            }
            cachedModel = new Model(path);
            cachedModelPath = path;
        }
        return cachedModel;
    }

    public static synchronized void releaseModel() {
        if (activeRecognitions > 0) {
            releasePending = true;
            return;
        }
        if (cachedModel != null) {
            cachedModel.close();
            cachedModel = null;
            cachedModelPath = null;
        }
    }

    private static class WavInfo {
        float sampleRate;
        long dataOffset;
    }

    private static WavInfo readWavInfo(File wavFile) throws Exception {
        WavInfo info = new WavInfo();
        try (RandomAccessFile raf = new RandomAccessFile(wavFile, "r")) {
            byte[] riffHeader = new byte[12];
            raf.readFully(riffHeader);

            if (!matches(riffHeader, 0, "RIFF") || !matches(riffHeader, 8, "WAVE")) {
                throw new Exception("Not a valid WAV file");
            }

            boolean foundFmt = false;
            boolean foundData = false;

            while (raf.getFilePointer() < raf.length() - 8) {
                byte[] chunkHeader = new byte[8];
                raf.readFully(chunkHeader);
                String chunkId = new String(chunkHeader, 0, 4, "US-ASCII");
                long chunkSize = readLE32(chunkHeader, 4) & 0xFFFFFFFFL;

                if (chunkId.equals("fmt ")) {
                    byte[] fmtBody = new byte[(int) chunkSize];
                    raf.readFully(fmtBody);
                    int sampleRate = readLE32(fmtBody, 4);
                    info.sampleRate = (float) sampleRate;
                    foundFmt = true;
                } else if (chunkId.equals("data")) {
                    info.dataOffset = raf.getFilePointer();
                    foundData = true;
                    break;
                } else {
                    long skip = chunkSize + (chunkSize % 2);
                    raf.seek(raf.getFilePointer() + skip);
                }
            }

            if (!foundFmt || !foundData) {
                throw new Exception("WAV file missing fmt or data chunk");
            }
        }
        return info;
    }

    private static boolean matches(byte[] data, int offset, String tag) {
        for (int i = 0; i < tag.length(); i++) {
            if (data[offset + i] != (byte) tag.charAt(i)) return false;
        }
        return true;
    }

    private static int readLE32(byte[] data, int offset) {
        return (data[offset] & 0xff) |
                ((data[offset + 1] & 0xff) << 8) |
                ((data[offset + 2] & 0xff) << 16) |
                ((data[offset + 3] & 0xff) << 24);
    }
}
