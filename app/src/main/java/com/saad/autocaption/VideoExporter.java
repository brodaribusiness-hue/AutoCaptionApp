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
            Uri sourceVideoUri,
            List<Caption> captions,
            SlotStyleConfig configBefore,
            SlotStyleConfig configActive,
            SlotStyleConfig configAfter,
            float fontSizeSp,
            int previewWidthPx,
            int previewHeightPx,
            CaptionSlotTransform beforeSlot,
            CaptionSlotTransform activeSlot,
            CaptionSlotTransform afterSlot,
            ExportCallback callback) {

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> callback.onProgress("Preparing export..."));

        new Thread(() -> {
            File tempSource = null;
            File tempAss = null;
            File tempOutput = null;

            try {
                tempSource = new File(context.getCacheDir(), "export_input_" + System.currentTimeMillis() + ".mp4");
                try (InputStream in = context.getContentResolver().openInputStream(sourceVideoUri);
                     OutputStream out = new FileOutputStream(tempSource)) {
                    byte[] buf = new byte[65536];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    out.flush();
                }

                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(tempSource.getAbsolutePath());
                String rotStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                int rotation = rotStr != null ? Integer.parseInt(rotStr) : 0;
                int rawW = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int rawH = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                mmr.release();

                int videoWidth = (rotation == 90 || rotation == 270) ? rawH : rawW;
                int videoHeight = (rotation == 90 || rotation == 270) ? rawW : rawH;

                File fontsDir = new File(context.getCacheDir(), "export_fonts");
                CaptionStyleOptions.prepareExportFont(context, configBefore.fontOption, fontsDir);
                CaptionStyleOptions.prepareExportFont(context, configActive.fontOption, fontsDir);
                CaptionStyleOptions.prepareExportFont(context, configAfter.fontOption, fontsDir);

                String assContent = AssSubtitleBuilder.build(
                        captions,
                        videoWidth,
                        videoHeight,
                        previewWidthPx,
                        previewHeightPx,
                        fontSizeSp,
                        configBefore,
                        configActive,
                        configAfter,
                        beforeSlot,
                        activeSlot,
                        afterSlot);

                tempAss = new File(context.getCacheDir(), "export_subs_" + System.currentTimeMillis() + ".ass");
                try (FileOutputStream fos = new FileOutputStream(tempAss)) {
                    fos.write(assContent.getBytes("UTF-8"));
                    fos.flush();
                }

                tempOutput = new File(context.getCacheDir(), "export_out_" + System.currentTimeMillis() + ".mp4");

                mainHandler.post(() -> callback.onProgress("Baking captions into video..."));

                String assEscaped = tempAss.getAbsolutePath()
                        .replace("\\", "/")
                        .replace(":", "\\:")
                        .replace("'", "\\'");
                String fontsEscaped = fontsDir.getAbsolutePath()
                        .replace("\\", "/")
                        .replace(":", "\\:")
                        .replace("'", "\\'");

                String vfFilter = String.format("subtitles='%s':fontsdir='%s'", assEscaped, fontsEscaped);

                String cmd = String.format(
                        "-y -i \"%s\" -vf \"%s\" -c:v libx264 -preset fast -crf 22 -c:a copy \"%s\"",
                        tempSource.getAbsolutePath(),
                        vfFilter,
                        tempOutput.getAbsolutePath());

                FFmpegSession session = FFmpegKit.execute(cmd);

                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    mainHandler.post(() -> callback.onProgress("Saving to gallery..."));
                    Uri galleryUri = saveToGallery(context, tempOutput);
                    mainHandler.post(() -> callback.onSuccess(galleryUri));
                } else {
                    String failMsg = "FFmpeg failed with state: " + session.getState();
                    mainHandler.post(() -> callback.onError(failMsg));
                }

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Export error: " + e.getMessage()));
            } finally {
                if (tempSource != null && tempSource.exists()) tempSource.delete();
                if (tempAss != null && tempAss.exists()) tempAss.delete();
                if (tempOutput != null && tempOutput.exists()) tempOutput.delete();
            }
        }).start();
    }

    private static Uri saveToGallery(Context context, File videoFile) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "AutoCaption_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AutoCaption");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }

        Uri uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("Failed to create MediaStore entry");

        try (InputStream in = new java.io.FileInputStream(videoFile);
             OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            byte[] buf = new byte[65536];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
        }

        return uri;
    }
}
