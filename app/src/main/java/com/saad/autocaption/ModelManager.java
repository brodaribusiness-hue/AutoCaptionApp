package com.saad.autocaption;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModelManager {

    private static final String TAG = "ModelManager";

    private static final String MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";

    private static final String MODEL_FOLDER_NAME =
            "vosk-model-small-en-us-0.15";

    private static final String MODEL_MARKER_FILE = "am/final.mdl";

    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 30000;

    public interface ModelCallback {
        void onProgress(String message);
        void onSuccess(File modelDir);
        void onError(String message);
    }

    public static boolean isModelReady(Context context) {
        File modelDir = getModelDir(context);
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            return false;
        }
        File marker = new File(modelDir, MODEL_MARKER_FILE);
        if (marker.exists() && marker.length() > 0) {
            return true;
        }
        // Check if marker exists directly inside subfolder
        File[] children = modelDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    File nestedMarker = new File(child, MODEL_MARKER_FILE);
                    if (nestedMarker.exists() && nestedMarker.length() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static File getModelDir(Context context) {
        File targetDir = new File(context.getFilesDir(), MODEL_FOLDER_NAME);
        File marker = new File(targetDir, MODEL_MARKER_FILE);
        if (marker.exists() && marker.length() > 0) {
            return targetDir;
        }
        // Fallback for nested folder
        File[] children = targetDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && new File(child, MODEL_MARKER_FILE).exists()) {
                    return child;
                }
            }
        }
        return targetDir;
    }

    public static void downloadAndSetupModel(Context context, ModelCallback callback) {
        if (isModelReady(context)) {
            callback.onSuccess(getModelDir(context));
            return;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            File zipFile = new File(context.getCacheDir(), "model_" + System.currentTimeMillis() + ".zip");
            File tempExtractDir = new File(context.getCacheDir(), "model_extract_" + System.currentTimeMillis());

            try {
                postIfAlive(context, mainHandler, () -> callback.onProgress("Connecting to speech model server..."));

                downloadWithRedirects(MODEL_URL, zipFile, context, mainHandler, callback);

                postIfAlive(context, mainHandler, () -> callback.onProgress("Extracting model archive..."));

                if (!tempExtractDir.exists()) tempExtractDir.mkdirs();
                unzip(zipFile, tempExtractDir);

                // Find valid model root containing marker file
                File validExtractedRoot = findModelRoot(tempExtractDir);
                if (validExtractedRoot == null) {
                    throw new Exception("Archive does not contain a valid Vosk model directory");
                }

                File finalModelDir = new File(context.getFilesDir(), MODEL_FOLDER_NAME);
                if (finalModelDir.exists()) {
                    deleteRecursive(finalModelDir);
                }

                // Safe recursive copy instead of atomic rename
                copyDirectory(validExtractedRoot, finalModelDir);

                zipFile.delete();
                deleteRecursive(tempExtractDir);

                if (!isModelReady(context)) {
                    throw new Exception("Model verification failed after extraction");
                }

                File readyDir = getModelDir(context);
                postIfAlive(context, mainHandler, () -> callback.onSuccess(readyDir));

            } catch (Exception e) {
                Log.e(TAG, "Speech model setup error", e);
                if (zipFile.exists()) zipFile.delete();
                if (tempExtractDir.exists()) deleteRecursive(tempExtractDir);

                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                postIfAlive(context, mainHandler, () -> callback.onError("Model setup failed: " + msg));
            }
        }).start();
    }

    private static File findModelRoot(File root) {
        if (new File(root, MODEL_MARKER_FILE).exists()) {
            return root;
        }
        File[] list = root.listFiles();
        if (list != null) {
            for (File f : list) {
                if (f.isDirectory()) {
                    File found = findModelRoot(f);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private static void copyDirectory(File source, File destination) throws Exception {
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new Exception("Cannot create target directory: " + destination.getAbsolutePath());
            }
            String[] children = source.list();
            if (children != null) {
                for (String child : children) {
                    copyDirectory(new File(source, child), new File(destination, child));
                }
            }
        } else {
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (InputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buf = new byte[65536];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.flush();
            }
        }
    }

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

    private static void downloadWithRedirects(
            String initialUrl,
            File outputFile,
            Context context,
            Handler mainHandler,
            ModelCallback callback) throws Exception {

        String currentUrl = initialUrl;
        HttpURLConnection connection = null;

        for (int redirectCount = 0; redirectCount < 5; redirectCount++) {
            URL url = new URL(currentUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == 307 || status == 308) {
                String newUrl = connection.getHeaderField("Location");
                connection.disconnect();
                if (newUrl != null && !newUrl.isEmpty()) {
                    currentUrl = newUrl;
                    continue;
                }
            }

            if (status != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new Exception("Server responded with HTTP " + status);
            }

            int fileLength = connection.getContentLength();
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[16384];
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
                                    callback.onProgress("Downloading speech model... " + finalPercent + "%"));
                        }
                    }
                }
                output.flush();
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new Exception("Too many redirects while downloading speech model");
    }

    private static void unzip(File zipFile, File targetDirectory) throws Exception {
        String canonicalTargetPath = targetDirectory.getCanonicalPath();

        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                File newFile = new File(targetDirectory, entry.getName());
                String canonicalNewFilePath = newFile.getCanonicalPath();

                if (!canonicalNewFilePath.startsWith(canonicalTargetPath)) {
                    throw new Exception("Zip entry path traversal error: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[16384];
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        fos.flush();
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
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
