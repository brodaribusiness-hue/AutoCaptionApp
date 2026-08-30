package com.saad.autocaption;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModelManager {

    private static final String MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";

    private static final String MODEL_FOLDER_NAME =
            "vosk-model-small-en-us-0.15";

    // FIX: marker file used to confirm the model extracted completely,
    // instead of just checking that the folder exists.
    private static final String MODEL_MARKER_FILE = "am/final.mdl";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;

    public interface ModelCallback {
        void onProgress(String message);
        void onSuccess(File modelDir);
        void onError(String message);
    }

    public static boolean isModelReady(Context context) {
        File modelDir = new File(context.getFilesDir(), MODEL_FOLDER_NAME);
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            return false;
        }
        // FIX: verify a known internal file exists too, so a partial/
        // interrupted extraction isn't mistaken for a ready model.
        File marker = new File(modelDir, MODEL_MARKER_FILE);
        return marker.exists() && marker.length() > 0;
    }

    public static File getModelDir(Context context) {
        return new File(context.getFilesDir(), MODEL_FOLDER_NAME);
    }

    public static void downloadAndSetupModel(
            Context context,
            ModelCallback callback) {

        if (isModelReady(context)) {
            callback.onSuccess(getModelDir(context));
            return;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                File zipFile = new File(
                        context.getCacheDir(),
                        "model_" + System.currentTimeMillis() + ".zip");
                File tempExtractDir = new File(
                        context.getCacheDir(),
                        "model_extract_" + System.currentTimeMillis());
                try {
                    postIfAlive(context, mainHandler, () ->
                            callback.onProgress("Downloading speech model..."));

                    downloadFile(MODEL_URL, zipFile, context, mainHandler, callback);

                    postIfAlive(context, mainHandler, () ->
                            callback.onProgress("Extracting model..."));

                    // FIX: extract into a temp folder first, then move it into
                    // place atomically — so a crash mid-extraction never leaves
                    // a folder in files-dir that looks "ready" but isn't.
                    tempExtractDir.mkdirs();
                    unzip(zipFile, tempExtractDir);

                    File finalModelDir = getModelDir(context);
                    File extractedModelDir = new File(tempExtractDir, MODEL_FOLDER_NAME);

                    if (!extractedModelDir.exists()) {
                        throw new Exception(
                                "Extracted archive did not contain expected model folder");
                    }

                    if (finalModelDir.exists()) {
                        deleteRecursive(finalModelDir);
                    }
                    if (!extractedModelDir.renameTo(finalModelDir)) {
                        throw new Exception("Could not move extracted model into place");
                    }

                    zipFile.delete();
                    deleteRecursive(tempExtractDir);

                    if (!isModelReady(context)) {
                        throw new Exception("Model extraction incomplete");
                    }

                    postIfAlive(context, mainHandler, () -> callback.onSuccess(finalModelDir));

                } catch (Exception e) {
                    zipFile.delete();
                    deleteRecursive(tempExtractDir);
                    String message = e.getMessage() != null ? e.getMessage() : e.toString();
                    postIfAlive(context, mainHandler, () ->
                            callback.onError("Model setup failed: " + message));
                }
            }
        }).start();
    }

    // FIX: skip posting UI callbacks if the owning Activity has already
    // finished/been destroyed (prevents crashes touching dead views).
    private static void postIfAlive(Context context, Handler handler, Runnable r) {
        handler.post(() -> {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
            }
            r.run();
        });
    }

    private static void downloadFile(
            String urlString,
            File outputFile,
            Context context,
            Handler mainHandler,
            ModelCallback callback) throws Exception {

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);

        try {
            connection.connect();

            // FIX: verify the server actually returned the file (not an
            // error page) before treating the response body as a zip.
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception(
                        "Server returned HTTP " + responseCode + " while downloading model");
            }

            int fileLength = connection.getContentLength();

            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                int lastPercent = -1;

                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    output.write(buffer, 0, count);

                    if (fileLength > 0) {
                        int percent = (int) (total * 100 / fileLength);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            int finalPercent = percent;
                            postIfAlive(context, mainHandler, () ->
                                    callback.onProgress(
                                            "Downloading speech model... " + finalPercent + "%"));
                        }
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void unzip(File zipFile, File targetDirectory) throws Exception {

        String canonicalTargetPath = targetDirectory.getCanonicalPath();

        try (ZipInputStream zipInputStream =
                     new ZipInputStream(new FileInputStream(zipFile))) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {

                File newFile = new File(targetDirectory, entry.getName());

                // FIX (Zip Slip): make sure the resolved path is actually
                // inside targetDirectory before writing anything there.
                String canonicalNewFilePath = newFile.getCanonicalPath();
                if (!canonicalNewFilePath.equals(canonicalTargetPath)
                        && !canonicalNewFilePath.startsWith(canonicalTargetPath + File.separator)) {
                    throw new Exception("Zip entry is outside target dir: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
