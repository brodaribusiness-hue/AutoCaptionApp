package com.saad.autocaption;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class SpeechToText {

    private static final String TAG = "SpeechToText";

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
            FileInputStream inputStream = null;

            synchronized (SpeechToText.class) {
                activeRecognitions++;
            }

            try {
                mainHandler.post(() -> callback.onProgress("Initializing speech engine..."));

                // 1. Thread-safe Model Initializer / Cache
                Model model;
                synchronized (SpeechToText.class) {
                    String targetPath = modelDir.getAbsolutePath();
                    if (cachedModel == null || !targetPath.equals(cachedModelPath)) {
                        if (cachedModel != null) {
                            try {
                                cachedModel.close();
                            } catch (Exception ignored) {}
                        }
                        cachedModel = new Model(targetPath);
                        cachedModelPath = targetPath;
                    }
                    model = cachedModel;
                }

                // 2. Standard 16kHz Recognizer Setup
                float sampleRate = 16000.0f;
                recognizer = new Recognizer(model, sampleRate);
                recognizer.setWords(true);

                inputStream = new FileInputStream(wavFile);
                long totalBytes = wavFile.length();
                if (totalBytes > 44) {
                    long skipped = inputStream.skip(44); // Skip standard 44-byte WAV header
                    totalBytes -= skipped;
                }

                byte[] buffer = new byte[8192];
                int bytesRead;
                long processedBytes = 0;
                int lastReportedPercent = -1;
                List<String> jsonResults = new ArrayList<>();

                mainHandler.post(() -> callback.onProgress("Transcribing audio... 0%"));

                // 3. Streaming buffer loop
                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    if (bytesRead > 0) {
                        processedBytes += bytesRead;
                        if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                            String result = recognizer.getResult();
                            if (result != null && !result.trim().isEmpty()) {
                                jsonResults.add(result);
                            }
                        }

                        if (totalBytes > 0) {
                            int percent = (int) ((processedBytes * 100) / totalBytes);
                            if (percent != lastReportedPercent && percent <= 100) {
                                lastReportedPercent = percent;
                                int finalPercent = percent;
                                mainHandler.post(() -> callback.onProgress("Transcribing audio... " + finalPercent + "%"));
                            }
                        }
                    }
                }

                // 4. Capture Final Buffer Chunk (Vital for last words)
                String finalResult = recognizer.getFinalResult();
                if (finalResult != null && !finalResult.trim().isEmpty()) {
                    jsonResults.add(finalResult);
                }

                mainHandler.post(() -> callback.onSuccess(jsonResults));

            } catch (Exception e) {
                Log.e(TAG, "Recognition error", e);
                mainHandler.post(() -> callback.onError("Speech recognition failed: " + e.getMessage()));
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (Exception ignored) {}
                }
                if (recognizer != null) {
                    try {
                        recognizer.close();
                    } catch (Exception ignored) {}
                }

                // 5. Safe native cleanup when all background jobs complete
                synchronized (SpeechToText.class) {
                    activeRecognitions--;
                    if (activeRecognitions == 0 && releasePending) {
                        if (cachedModel != null) {
                            try {
                                cachedModel.close();
                            } catch (Exception ignored) {}
                            cachedModel = null;
                            cachedModelPath = null;
                        }
                        releasePending = false;
                    }
                }
            }
        }).start();
    }

    public static synchronized void releaseModel() {
        if (activeRecognitions > 0) {
            releasePending = true;
        } else {
            if (cachedModel != null) {
                try {
                    cachedModel.close();
                } catch (Exception ignored) {}
                cachedModel = null;
                cachedModelPath = null;
            }
            releasePending = false;
        }
    }
}
