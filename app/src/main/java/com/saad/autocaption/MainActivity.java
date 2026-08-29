package com.saad.autocaption;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.widget.Button;
import android.widget.TextView;
import android.media.MediaPlayer;
import java.io.File;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO = 100;

    private SurfaceView videoSurface;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private TextView statusText;
    private Uri videoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        videoSurface = (SurfaceView) findViewById(R.id.videoSurface);
        Button button = (Button) findViewById(R.id.selectVideoButton);
        statusText = (TextView) findViewById(R.id.statusText);

        surfaceHolder = videoSurface.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        videoSurface.setZOrderMediaOverlay(true);

        surfaceHolder.addCallback(new SurfaceHolder.Callback() {

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (videoUri != null) {
                    playVideo(videoUri);
                }
            }

            @Override
            public void surfaceChanged(
                    SurfaceHolder holder,
                    int format,
                    int width,
                    int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
            }
        });

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("video/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                startActivityForResult(intent, PICK_VIDEO);
            }
        });
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_VIDEO &&
                resultCode == RESULT_OK &&
                data != null) {

            videoUri = data.getData();

            playVideo(videoUri);

            extractAudio(videoUri);
        }
    }

    private void extractAudio(Uri uri) {

        statusText.setText("Extracting audio...");

        AudioExtractor.extractAudioToWav(
                this,
                uri,
                new AudioExtractor.ExtractCallback() {

                    @Override
                    public void onSuccess(File wavFile) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText(
                                        "Audio extracted: " +
                                        wavFile.getName() +
                                        " (" + wavFile.length() +
                                        " bytes)");
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText(message);
                            }
                        });
                    }
                });
    }

    private void playVideo(Uri uri) {

        try {

            if (mediaPlayer != null) {
                mediaPlayer.reset();
            } else {
                mediaPlayer = new MediaPlayer();
            }

            mediaPlayer.setDataSource(this, uri);

            mediaPlayer.setDisplay(surfaceHolder);

            mediaPlayer.setOnPreparedListener(
                    new MediaPlayer.OnPreparedListener() {

                @Override
                public void onPrepared(MediaPlayer mp) {

                    mp.setLooping(true);
                    mp.start();
                }
            });

            mediaPlayer.setOnErrorListener(
                    new MediaPlayer.OnErrorListener() {

                @Override
                public boolean onError(
                        MediaPlayer mp,
                        int what,
                        int extra) {

                    statusText.setText("Playback error");
                    return true;
                }
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {

            statusText.setText(
                    "Error: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
