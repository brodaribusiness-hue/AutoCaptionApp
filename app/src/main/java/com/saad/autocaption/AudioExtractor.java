package com.saad.autocaption;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class AudioExtractor {

    public interface ExtractCallback {
        void onSuccess(File wavFile);
        void onError(String message);
    }

    public static void extractAudioToWav(Context context, Uri videoUri, ExtractCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            File tempInput = null;
            File wavFile = null;

            try {
                // 1. Copy video stream into cache
                String uid = UUID.randomUUID().toString();
                tempInput = new File(context.getCacheDir(), "input_" + uid + ".mp4");
                wavFile = new File(context.getCacheDir(), "audio_16k_" + uid + ".wav");

                try (InputStream in = context.getContentResolver().openInputStream(videoUri);
                     OutputStream out = new FileOutputStream(tempInput)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }

                // 2. High-speed native FFmpeg extraction (Strict 16kHz 16-bit Mono WAV for Vosk)
                String cmd = String.format(
                        "-y -i \"%s\" -vn -acodec pcm_s16le -ar 16000 -ac 1 \"%s\"",
                        tempInput.getAbsolutePath(),
                        wavFile.getAbsolutePath());

                FFmpegSession session = FFmpegKit.execute(cmd);

                if (ReturnCode.isSuccess(session.getReturnCode()) && wavFile.exists() && wavFile.length() > 44) {
                    File resultFile = wavFile;
                    mainHandler.post(() -> callback.onSuccess(resultFile));
                } else {
                    String errorMsg = "Audio conversion failed: " + session.getFailStackTrace();
                    mainHandler.post(() -> callback.onError(errorMsg));
                }

            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                mainHandler.post(() -> callback.onError("Extraction error: " + msg));
            } finally {
                if (tempInput != null && tempInput.exists()) {
                    tempInput.delete();
                }
            }
        }).start();
    }
}
