package com.saad.autocaption;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class VideoExporter {

    public interface ExportCallback {
        void onProgress(String message);
        void onSuccess(Uri savedUri);
        void onError(String message);
    }

    public static void export(
        Context context,
        Uri videoUri,
        List<Caption> captions,
        CaptionStyleOptions.FontOption fontOption,
        float fontSizePreviewPx,      // CHANGED from fontSizeSp
        int highlightColor,
        CaptionStyleOptions.CaptionStyleType style,
        int previewWidthPx,
        int previewHeightPx,
        CaptionSlotTransform beforeSlot,
        CaptionSlotTransform activeSlot,
        CaptionSlotTransform afterSlot,
        ExportCallback callback) {

    Handler mainHandler = new Handler(Looper.getMainLooper());

    // GUARD: reject export if preview wasn't measured yet — otherwise \pos math
    // divides by near-zero and captions burn in at garbage coordinates.
    if (previewWidthPx <= 0 || previewHeightPx <= 0) {
        mainHandler.post(() -> callback.onError(
                "Preview not ready yet — try again in a moment"));
        return;
    }

    new Thread(() -> {
        // ... existing code ...

        mainHandler.post(() -> callback.onProgress("Building captions..."));
        String assContent = AssSubtitleBuilder.build(
                captions, videoWidth, videoHeight,
                previewWidthPx, previewHeightPx,
                familyName, fontSizePreviewPx, highlightColor, style,   // CHANGED param name
                beforeSlot, activeSlot, afterSlot);

        // ... existing code ...
    }).start();
}

    private static void copyUriToFile(Context context, Uri uri, File dest) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
        }
    }

    private static int[] readVideoDimensions(Context context, Uri uri) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            int width = Integer.parseInt(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = Integer.parseInt(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            String rotationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            int rotation = rotationStr != null ? Integer.parseInt(rotationStr) : 0;
            if (rotation == 90 || rotation == 270) {
                int tmp = width; width = height; height = tmp;
            }
            return new int[]{width, height};
        } finally {
            retriever.release();
        }
    }

    private static Uri saveToGallery(Context context, File videoFile) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }

        Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri itemUri = context.getContentResolver().insert(collection, values);
        if (itemUri == null) throw new Exception("Could not create MediaStore entry");

        try (InputStream in = new java.io.FileInputStream(videoFile);
             OutputStream out = context.getContentResolver().openOutputStream(itemUri)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            context.getContentResolver().update(itemUri, values, null, null);
        }

        return itemUri;
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }
}
