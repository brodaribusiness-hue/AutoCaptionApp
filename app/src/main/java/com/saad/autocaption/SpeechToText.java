package com.saad.autocaption;

import android.os.Handler;
import android.os.Looper;

import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileInputStream;

public class SpeechToText {

    public interface ResultCallback {
        void onProgress(String message);
        void onSuccess(String jsonResult);
        void onError(String message);
    }

    public static void recognize(
            File modelDir,
            File wavFile,
            ResultCallback callback) {

        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onProgress("Loading speech model...");
                        }
                    });

                    Model model = new Model(modelDir.getAbsolutePath());

                    Recognizer recognizer = new Recognizer(model, 16000.0f);
                    recognizer.setWords(true);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onProgress("Recognizing speech...");
                        }
                    });

                    FileInputStream inputStream =
                            new FileInputStream(wavFile);

                    inputStream.skip(44);

                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) >= 0) {
                        recognizer.acceptWaveForm(buffer, bytesRead);
                    }

                    String finalResult = recognizer.getFinalResult();

                    inputStream.close();
                    recognizer.close();
                    model.close();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(finalResult);
                        }
                    });

                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(
                                    "Speech recognition failed: " +
                                    e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }
}
