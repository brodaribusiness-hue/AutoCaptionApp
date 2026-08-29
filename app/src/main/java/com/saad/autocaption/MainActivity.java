package com.saad.autocaption;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.widget.Button;
import android.widget.TextView;
import android.media.MediaPlayer;
import java.io.File;
import java.util.List;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO = 100;

    private SurfaceView videoSurface;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private TextView statusText;
    private TextView captionText;
    private Uri videoUri;
    private List<Caption> captions;
    private Handler captionUpdateHandler;
    private Runnable captionUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        videoSurface = (SurfaceView) findViewById(R.id.videoSurface);
        captionText = (TextView) findViewById(R.id.captionText);
        Button button = (Button) findViewById(R.id.selectVideoButton);
        statusText = (TextView) findViewById(R.id.statusText);

        // Needed so setShadowLayer() (used for the glow effect) actually
        // renders — hardware-accelerated views ignore shadow layers.
        captionText.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        captionUpdateHandler = new Handler(Looper.getMainLooper());

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
                stopCaptionUpdates();
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
                                setupModelAndRecognize(wavFile);
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

    private void setupModelAndRecognize(File wavFile) {

        ModelManager.downloadAndSetupModel(
                this,
                new ModelManager.ModelCallback() {

                    @Override
                    public void onProgress(String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText(message);
                            }
                        });
                    }

                    @Override
                    public void onSuccess(File modelDir) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                runSpeechRecognition(modelDir, wavFile);
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

    private void runSpeechRecognition(File modelDir, File wavFile) {

        SpeechToText.recognize(
                modelDir,
                wavFile,
                new SpeechToText.ResultCallback() {

                    @Override
                    public void onProgress(String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText(message);
                            }
                        });
                    }

                    @Override
                    public void onSuccess(String jsonResult) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                captions = CaptionParser.parseVoskResult(jsonResult);
                                statusText.setText(
                                        "Captions ready! (" +
                                        captions.size() + " words)");
                                startCaptionUpdates();
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

    private void startCaptionUpdates() {
        captionUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying() &&
                        captions != null && !captions.isEmpty()) {

                    long currentTimeMs = mediaPlayer.getCurrentPosition();
                    float currentTimeSec = currentTimeMs / 1000.0f;

                    // The word actually being spoken right now (if any).
                    int matchedIndex = -1;
                    for (int i = 0; i < captions.size(); i++) {
                        Caption cap = captions.get(i);
                        if (currentTimeSec >= cap.startTime &&
                                currentTimeSec < cap.endTime) {
                            matchedIndex = i;
                            break;
                        }
                    }

                    // Anchor point for choosing which 4-5 word window to
                    // show, even during small gaps between words.
                    int anchorIndex = matchedIndex;
                    if (anchorIndex == -1) {
                        for (int i = 0; i < captions.size(); i++) {
                            if (captions.get(i).startTime > currentTimeSec) {
                                anchorIndex = Math.max(0, i - 1);
                                break;
                            }
                        }
                        if (anchorIndex == -1) {
                            anchorIndex = captions.size() - 1;
                        }
                    }

                    int wordsBefore = 2;
                    int wordsAfter = 2;
                    int startIdx = Math.max(0, anchorIndex - wordsBefore);
                    int endIdx = Math.min(
                            captions.size() - 1, anchorIndex + wordsAfter);

                    android.text.SpannableStringBuilder builder =
                            new android.text.SpannableStringBuilder();

                    for (int i = startIdx; i <= endIdx; i++) {
                        Caption cap = captions.get(i);

                        int start = builder.length();
                        builder.append(cap.word);
                        int end = builder.length();

                        if (i == matchedIndex) {
                            // Low, soft glow on the word currently being
                            // spoken.
                            builder.setSpan(
                                    new GlowSpan(
                                            0xFFFFFFFF,
                                            0xAAFFEB3B,
                                            10f),
                                    start, end, 0);
                            builder.setSpan(
                                    new android.text.style.StyleSpan(
                                            android.graphics.Typeface.BOLD),
                                    start, end, 0);
                        } else {
                            builder.setSpan(
                                    new android.text.style.ForegroundColorSpan(
                                            0xCCCCCCCC),
                                    start, end, 0);
                        }

                        if (i != endIdx) {
                            builder.append(" ");
                        }
                    }

                    captionText.setText(builder);
                }
                captionUpdateHandler.postDelayed(this, 100);
            }
        };
        captionUpdateHandler.post(captionUpdateRunnable);
    }

    private void stopCaptionUpdates() {
        if (captionUpdateRunnable != null) {
            captionUpdateHandler.removeCallbacks(captionUpdateRunnable);
        }
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

        stopCaptionUpdates();

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        SpeechToText.releaseModel();
    }
}
