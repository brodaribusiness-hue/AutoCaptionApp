package com.saad.autocaption;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import android.media.MediaPlayer;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO = 100;

    private SurfaceView videoSurface;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private TextView statusText;
    private TextView captionText;
    private Button generateCaptionsButton;
    private Button exportButton;
    private Spinner fontStyleSpinner;
    private Spinner captionColorSpinner;
    private Uri videoUri;
    private File extractedWavFile;
    private List<Caption> captions;
    private Handler captionUpdateHandler;
    private Runnable captionUpdateRunnable;

    private Typeface selectedTypeface = Typeface.SANS_SERIF;
    private int selectedColor = 0xFFFFEB3B; // default yellow

    // FIX: every time a new video is picked, currentRequestId advances.
    // Every async callback (audio extraction, model load, recognition)
    // captures the request id that was active when it started, and
    // re-checks it before touching shared state/UI. A stale callback
    // from a previously selected video is then simply ignored instead
    // of overwriting the current video's captions.
    private final AtomicInteger requestIdGenerator = new AtomicInteger(0);
    private volatile int currentRequestId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        videoSurface = (SurfaceView) findViewById(R.id.videoSurface);
        captionText = (TextView) findViewById(R.id.captionText);
        Button selectVideoButton = (Button) findViewById(R.id.selectVideoButton);
        generateCaptionsButton = (Button) findViewById(R.id.generateCaptionsButton);
        exportButton = (Button) findViewById(R.id.exportButton);
        statusText = (TextView) findViewById(R.id.statusText);
        fontStyleSpinner = (Spinner) findViewById(R.id.fontStyleSpinner);
        captionColorSpinner = (Spinner) findViewById(R.id.captionColorSpinner);

        captionText.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        captionText.setTypeface(selectedTypeface);

        generateCaptionsButton.setEnabled(false);
        exportButton.setEnabled(false);

        setupFontSpinner();
        setupColorSpinner();

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
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
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

        selectVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("video/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(intent, PICK_VIDEO);
            }
        });

        generateCaptionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoUri != null) {
                    // FIX: capture the request id for THIS generation run
                    // so async callbacks can tell whether they're still
                    // relevant by the time they complete.
                    final int requestId = currentRequestId;
                    generateCaptionsButton.setEnabled(false);
                    captions = null;
                    captionText.setText("");
                    extractAudio(videoUri, requestId);
                }
            }
        });

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                statusText.setText("Export coming soon");
            }
        });
    }

    private void setupFontSpinner() {
        CaptionStyleOptions.FontOption[] fonts = CaptionStyleOptions.getFontOptions();

        ArrayAdapter<CaptionStyleOptions.FontOption> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fonts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fontStyleSpinner.setAdapter(adapter);

        fontStyleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CaptionStyleOptions.FontOption chosen = fonts[position];
                selectedTypeface = CaptionStyleOptions.resolveTypeface(MainActivity.this, chosen);
                captionText.setTypeface(selectedTypeface);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupColorSpinner() {
        CaptionStyleOptions.ColorOption[] colors = CaptionStyleOptions.getColorOptions();

        ArrayAdapter<CaptionStyleOptions.ColorOption> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, colors);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        captionColorSpinner.setAdapter(adapter);

        captionColorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CaptionStyleOptions.ColorOption chosen = colors[position];
                if (chosen.color == 0) {
                    showCustomColorDialog();
                } else {
                    selectedColor = chosen.color;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void showCustomColorDialog() {
        EditText input = new EditText(this);
        input.setHint("#RRGGBB e.g. #FF00FF");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF888888);

        new AlertDialog.Builder(this)
                .setTitle("Custom Caption Color")
                .setView(input)
                .setPositiveButton("Apply", (dialog, which) -> {
                    String hex = input.getText().toString().trim();
                    try {
                        selectedColor = Color.parseColor(hex);
                    } catch (Exception e) {
                        statusText.setText("Invalid color code, keeping previous color");
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK && data != null) {

            videoUri = data.getData();

            // FIX: persist read permission on this URI so it survives
            // process death / activity recreation, not just this session.
            if (videoUri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                    // Some providers don't support persistable permissions —
                    // playback/extraction for this session still works fine.
                }
            }

            // FIX: bump the request id so any in-flight callback tied to
            // the PREVIOUSLY selected video is recognized as stale below
            // and ignored, instead of overwriting the new video's state.
            currentRequestId = requestIdGenerator.incrementAndGet();

            captions = null;
            captionText.setText("");
            exportButton.setEnabled(false);

            playVideo(videoUri);

            statusText.setText("Video loaded. Tap 'Generate Captions' to continue.");
            generateCaptionsButton.setEnabled(true);
        }
    }

    private void extractAudio(Uri uri, int requestId) {

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
                                if (requestId != currentRequestId) {
                                    // A newer video was picked while this was
                                    // running — discard the orphaned file.
                                    wavFile.delete();
                                    return;
                                }
                                extractedWavFile = wavFile;
                                setupModelAndRecognize(wavFile, requestId);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (requestId != currentRequestId) {
                                    return;
                                }
                                statusText.setText(message);
                                generateCaptionsButton.setEnabled(true);
                            }
                        });
                    }
                });
    }

    private void setupModelAndRecognize(File wavFile, int requestId) {

        ModelManager.downloadAndSetupModel(
                this,
                new ModelManager.ModelCallback() {

                    @Override
                    public void onProgress(String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (requestId != currentRequestId) {
                                    return;
                                }
                                statusText.setText(message);
                            }
                        });
                    }

                    @Override
                    public void onSuccess(File modelDir) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (requestId != currentRequestId) {
                                    return;
                                }
                                runSpeechRecognition(modelDir, wavFile, requestId);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (requestId != currentRequestId) {
                                    return;
                                }
                                statusText.setText(message);
                                generateCaptionsButton.setEnabled(true);
                            }
                        });
                    }
                });
    }

    private void runSpeechRecognition(File modelDir, File wavFile, int requestId) {

        SpeechToText.recognize(
                modelDir,
                wavFile,
                new SpeechToText.ResultCallback() {

                    @Override
                    public void onProgress(String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (requestId != currentRequestId) {
                                    return;
                                }
                                statusText.setText(message);
                            }
                        });
                    }

                    @Override
                    public void onSuccess(List<String> jsonResults) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (requestId != currentRequestId) {
                                    return;
                                }
                                // FIX: merge every utterance's JSON instead
                                // of parsing a single chunk — see
                                // CaptionParser.parseVoskResults().
                                captions = CaptionParser.parseVoskResults(jsonResults);
                                statusText.setText(
                                        "Captions ready! (" + captions.size() + " words)");
                                generateCaptionsButton.setEnabled(true);
                                exportButton.setEnabled(true);
                                startCaptionUpdates();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (requestId != currentRequestId) {
                                    return;
                                }
                                statusText.setText(message);
                                generateCaptionsButton.setEnabled(true);
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

                    int matchedIndex = -1;
                    for (int i = 0; i < captions.size(); i++) {
                        Caption cap = captions.get(i);
                        if (currentTimeSec >= cap.startTime && currentTimeSec < cap.endTime) {
                            matchedIndex = i;
                            break;
                        }
                    }

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
                    int endIdx = Math.min(captions.size() - 1, anchorIndex + wordsAfter);

                    android.text.SpannableStringBuilder builder =
                            new android.text.SpannableStringBuilder();

                    for (int i = startIdx; i <= endIdx; i++) {
                        Caption cap = captions.get(i);

                        int start = builder.length();
                        builder.append(cap.word);
                        int end = builder.length();

                        if (i == matchedIndex) {
                            int glowColor = (selectedColor & 0x00FFFFFF) | 0xAA000000;

                            builder.setSpan(
                                    new GlowSpan(selectedColor, glowColor, 10f),
                                    start, end, 0);
                            builder.setSpan(
                                    new android.text.style.StyleSpan(
                                            android.graphics.Typeface.BOLD),
                                    start, end, 0);
                        } else {
                            builder.setSpan(
                                    new android.text.style.ForegroundColorSpan(0xCCCCCCCC),
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

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                    mp.start();
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    statusText.setText("Playback error");
                    return true;
                }
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            statusText.setText("Error: " + e.getMessage());
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

        // FIX: clean up the last extracted-audio temp file so cache
        // doesn't slowly accumulate leftover files across sessions.
        if (extractedWavFile != null) {
            extractedWavFile.delete();
        }

        SpeechToText.releaseModel();
    }
}
