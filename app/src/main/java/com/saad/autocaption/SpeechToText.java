package com.saad.autocaption;

import android.os.Handler;
import android.os.Looper;

import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;

public class SpeechToText {

    public interface ResultCallback {
        void onProgress(String message);
        void onSuccess(String jsonResult);
        void onError(String message);
    }

    // Simple in-process model cache so we don't reload the (large) Vosk
    // model from disk every time the user picks a new video.
    private static Model cachedModel;
    private static String cachedModelPath;

    public static void recognize(
            File modelDir,
            File wavFile,
            ResultCallback callback) {

        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {

                Recognizer recognizer = null;
                FileInputStream inputStream = null;

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

                    mainHandler.post(() -> callback.onProgress("Recognizing speech..."));

                    inputStream = new FileInputStream(wavFile);
                    // Skip to the actual start of PCM audio data, not a
                    // hardcoded 44 bytes — WAV headers can carry extra
                    // chunks (LIST, fmt extensions, etc.) before "data".
                    long skipped = inputStream.skip(wavInfo.dataOffset);
                    if (skipped != wavInfo.dataOffset) {
                        throw new Exception("Could not seek to WAV audio data");
                    }

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    Recognizer finalRecognizer = recognizer;

                    while ((bytesRead = inputStream.read(buffer)) >= 0) {
                        finalRecognizer.acceptWaveForm(buffer, bytesRead);
                    }

                    String finalResult = recognizer.getFinalResult();

                    mainHandler.post(() -> callback.onSuccess(finalResult));

                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError(
                            "Speech recognition failed: " + e.getMessage()));
                } finally {
                    // Always release native/file resources, even on error,
                    // to avoid leaking memory or file handles.
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (Exception ignored) {
                        }
                    }
                    if (recognizer != null) {
                        recognizer.close();
                    }
                    // Note: the cached Model itself is intentionally NOT
                    // closed here — it's reused across calls. Call
                    // releaseModel() when the app no longer needs it
                    // (e.g. onDestroy of the last activity that uses it).
                }
            }
        }).start();
    }

    /**
     * Returns a cached Model if one is already loaded for this modelDir,
     * otherwise loads it from disk. Vosk models are large, so avoiding a
     * reload on every video saves significant time and memory churn.
     */
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

    /** Call this when the app is done with speech recognition entirely
     * (e.g. in Application.onTerminate or the last Activity's onDestroy)
     * to free the native model memory. */
    public static synchronized void releaseModel() {
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

    /**
     * Properly walks the WAV RIFF chunk structure to find the "fmt " chunk
     * (for sample rate) and the "data" chunk (for the real audio start
     * offset), instead of assuming a fixed 44-byte header.
     */
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
                    // sampleRate is bytes 4-7 of the fmt chunk body
                    int sampleRate = readLE32(fmtBody, 4);
                    info.sampleRate = (float) sampleRate;
                    foundFmt = true;

                } else if (chunkId.equals("data")) {
                    info.dataOffset = raf.getFilePointer();
                    foundData = true;
                    break; // audio data starts here; stop scanning

                } else {
                    // Skip any other chunk (LIST, fact, etc.), padded to
                    // an even number of bytes per the RIFF spec.
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
            if (data[offset + i] != (byte) tag.charAt(i)) {
                return false;
            }
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
