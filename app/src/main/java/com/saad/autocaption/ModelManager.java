package com.saad.autocaption;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
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

    public interface ModelCallback {
        void onProgress(String message);
        void onSuccess(File modelDir);
        void onError(String message);
    }

    public static boolean isModelReady(Context context) {
        File modelDir = new File(
                context.getFilesDir(), MODEL_FOLDER_NAME);
        return modelDir.exists() && modelDir.isDirectory();
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
                try {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onProgress("Downloading speech model...");
                        }
                    });

                    File zipFile = new File(
                            context.getCacheDir(), "model.zip");

                    downloadFile(MODEL_URL, zipFile, mainHandler, callback);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onProgress("Extracting model...");
                        }
                    });

                    unzip(zipFile, context.getFilesDir());

                    zipFile.delete();

                    File modelDir = getModelDir(context);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(modelDir);
                        }
                    });

                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(
                                    "Model setup failed: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private static void downloadFile(
            String urlString,
            File outputFile,
            Handler mainHandler,
            ModelCallback callback) throws Exception {

        URL url = new URL(urlString);
        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();
        connection.connect();

        int fileLength = connection.getContentLength();

        InputStream input = connection.getInputStream();
        FileOutputStream output = new FileOutputStream(outputFile);

        byte[] buffer = new byte[4096];
        long total = 0;
        int count;

        while ((count = input.read(buffer)) != -1) {
            total += count;
            output.write(buffer, 0, count);

            if (fileLength > 0) {
                int progress = (int) (total * 100 / fileLength);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onProgress(
                                "Downloading speech model... " +
                                progress + "%");
                    }
                });
            }
        }

        output.close();
        input.close();
    }

    private static void unzip(
            File zipFile,
            File targetDirectory) throws Exception {

        ZipInputStream zipInputStream = new ZipInputStream(
                new java.io.FileInputStream(zipFile));

        ZipEntry entry;

        while ((entry = zipInputStream.getNextEntry()) != null) {

            File newFile = new File(
                    targetDirectory, entry.getName());

            if (entry.isDirectory()) {
                newFile.mkdirs();
            } else {
                File parent = newFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }

                FileOutputStream fos = new FileOutputStream(newFile);

                byte[] buffer = new byte[4096];
                int len;
                while ((len = zipInputStream.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
            }

            zipInputStream.closeEntry();
        }

        zipInputStream.close();
    }
}
