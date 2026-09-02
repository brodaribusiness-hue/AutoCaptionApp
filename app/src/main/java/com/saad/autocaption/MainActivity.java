package com.saad.autocaption;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import android.media.MediaPlayer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private SurfaceView videoSurface;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private TextView statusText;
    private TextView captionText;
    private Button generateCaptionsButton;
    private Button exportButton;
    private Spinner fontStyleSpinner;
    private Spinner captionColorSpinner;
    private Spinner captionStyleSpinner;
    private AspectRatioFrameLayout videoPreviewContainer;
    private Uri videoUri;
    private File extractedWavFile;
    private List<Caption> captions;
    private Handler captionUpdateHandler;
    private Runnable captionUpdateRunnable;

    private Typeface selectedTypeface = Typeface.SANS_SERIF;
    private CaptionStyleOptions.FontOption selectedFontOption;
    private int selectedColor = 0xFFFFEB3B;

    private final float selectedFontSizeSp = 22f;
    private final int selectedGravity = Gravity.BOTTOM;

    private CaptionStyleOptions.CaptionStyleType selectedStyle =
            CaptionStyleOptions.CaptionStyleType.HIGHLIGHT_POP;

    private final AtomicInteger requestIdGenerator = new AtomicInteger(0);
    private volatile int currentRequestId = 0;

    private ActivityResultLauncher<Intent> pickVideoLauncher;
    private ActivityResultLauncher<String> storagePermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        pickVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handlePickedVideo(result.getData());
                    }
                });

        storagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        statusText.setText(
                                "Storage permission denied — export may not work on this device");
                    }
                });

        requestStoragePermissionIfNeeded();

        videoSurface = (SurfaceView) findViewById(R.id.videoSurface);
        captionText = (TextView) findViewById(R.id.captionText);
        videoPreviewContainer = (AspectRatioFrameLayout) findViewById(R.id.videoPreviewContainer);
        Button selectVideoButton = (Button) findViewById(R.id.selectVideoButton);
        generateCaptionsButton = (Button) findViewById(R.id.generateCaptionsButton);
        exportButton = (Button) findViewById(R.id.exportButton);
        statusText = (TextView) findViewById(R.id.statusText);
        fontStyleSpinner = (Spinner) findViewById(R.id.fontStyleSpinner);
        captionColorSpinner = (Spinner) findViewById(R.id.captionColorSpinner);
        captionStyleSpinner = (Spinner) findViewById(R.id.captionStyleSpinner);

        captionText.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        captionText.setTypeface(selectedTypeface);
        captionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, selectedFontSizeSp);
        applyCaptionGravity(selectedGravity);

        generateCaptionsButton.setEnabled(false);
        exportButton.setEnabled(false);

        setupFontSpinner();
        setupColorSpinner();
        setupStyleSpinner();

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
                pickVideoLauncher.launch(intent);
            }
        });

        generateCaptionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoUri != null) {
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
                if (captions == null || captions.isEmpty() || videoUri == null) {
                    statusText.setText("Generate captions before exporting");
                    return;
                }
                exportButton.setEnabled(false);
                generateCaptionsButton.setEnabled(false);

                VideoExporter.export(
                        MainActivity.this,
                        videoUri,
                        captions,
                        selectedFontOption,
                        selectedFontSizeSp,
                        selectedColor,
                        selectedGravity,
                        selectedStyle,
                        new VideoExporter.ExportCallback() {
                            @Override
                            public void onProgress(String message) {
                                runOnUiThread(() -> statusText.setText(message));
                            }

                            @Override
                            public void onSuccess(Uri savedUri) {
                                runOnUiThread(() -> {
                                    statusText.setText("Saved to gallery!");
                                    exportButton.setEnabled(true);
                                    generateCaptionsButton.setEnabled(true);
                                });
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> {
                                    statusText.setText(message);
                                    exportButton.setEnabled(true);
                                    generateCaptionsButton.setEnabled(true);
                                });
                            }
                        });
            }
        });
    }

    private void requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            boolean granted = ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                storagePermissionLauncher.launch(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    private void setupFontSpinner() {
        CaptionStyleOptions.FontOption[] fonts = CaptionStyleOptions.getFontOptions();
        selectedFontOption = fonts[0];

        ArrayAdapter<CaptionStyleOptions.FontOption> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fonts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fontStyleSpinner.setAdapter(adapter);

        fontStyleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CaptionStyleOptions.FontOption chosen = fonts[position];
                selectedFontOption = chosen;
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

    private void setupStyleSpinner() {
        CaptionStyleOptions.StyleOption[] styles = CaptionStyleOptions.getStyleOptions();

        ArrayAdapter<CaptionStyleOptions.StyleOption> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, styles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        captionStyleSpinner.setAdapter(adapter);

        captionStyleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedStyle = styles[position].type;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void applyCaptionGravity(int gravity) {
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) captionText.getLayoutParams();
        params.gravity = gravity;
        captionText.setLayoutParams(params);
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

    private void handlePickedVideo(Intent data) {

        videoUri = data.getData();

        if (videoUri != null) {
            try {
                getContentResolver().takePersistableUriPermission(
                        videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        }

        currentRequestId = requestIdGenerator.incrementAndGet();

        captions = null;
        captionText.setText("");
        exportButton.setEnabled(false);

        playVideo(videoUri);

        statusText.setText("Video loaded. Tap 'Generate Captions' to continue.");
        generateCaptionsButton.setEnabled(true);
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
                                    wavFile.delete();
                                    return;
                                }
                                if (extractedWavFile != null
                                        && !extractedWavFile.equals(wavFile)) {
                                    extractedWavFile.delete();
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

    private Object buildActiveWordSpan() {
        switch (selectedStyle) {
            case HIGHLIGHT_POP:
                return new android.text.style.ForegroundColorSpan(0xFFFF9800);

            case GREEN_EMPHASIS:
                return new android.text.style.ForegroundColorSpan(0xFF4CAF50);

            case KARAOKE_FLOW:
                return new KaraokeFillSpan(0xFFFFFFFF, selectedColor, 8f);

            case ONE_WORD_PUNCH:
                return new PopScaleSpan(selectedColor, 1.8f);

            case BOX_HIGHLIGHT:
                return new BackgroundBoxSpan(0xFF000000, selectedColor, 12f, 16f);

            case BOUNCE:
                return new BounceSpan(selectedColor);

            case GLOW_POP:
                int glowColor = (selectedColor & 0x00FFFFFF) | 0x80000000;
                return new GlowPopSpan(selectedColor, glowColor, 1.15f, 5f);

            case MINIMAL_CLEAN:
            default:
                return null;
        }
    }

    private int restWordColor() {
        switch (selectedStyle) {
            case HIGHLIGHT_POP:
            case GREEN_EMPHASIS:
            case MINIMAL_CLEAN:
                return 0xFFFFFFFF;
            default:
                return 0xCCCCCCCC;
        }
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

                    android.text.SpannableStringBuilder builder =
                            new android.text.SpannableStringBuilder();

                    if (selectedStyle == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH) {
                        if (matchedIndex != -1) {
                            Caption cap = captions.get(matchedIndex);
                            builder.append(cap.word);
                            builder.setSpan(buildActiveWordSpan(), 0, builder.length(), 0);
                            builder.setSpan(
                                    new android.text.style.StyleSpan(
                                            android.graphics.Typeface.BOLD),
                                    0, builder.length(), 0);
                        }
                        captionText.setText(builder);
                        captionUpdateHandler.postDelayed(this, 100);
                        return;
                    }

                    int wordsBefore = 1;
                    int wordsAfter = 1;
                    int startIdx = Math.max(0, anchorIndex - wordsBefore);
                    int endIdx = Math.min(captions.size() - 1, anchorIndex + wordsAfter);

                    for (int i = startIdx; i <= endIdx; i++) {
                        Caption cap = captions.get(i);

                        int start = builder.length();
                        builder.append(cap.word);
                        int end = builder.length();

                        if (selectedStyle == CaptionStyleOptions.CaptionStyleType.MINIMAL_CLEAN) {
                            builder.setSpan(
                                    new android.text.style.ForegroundColorSpan(0xFFFFFFFF),
                                    start, end, 0);
                            builder.setSpan(
                                    new android.text.style.StyleSpan(
                                            android.graphics.Typeface.BOLD),
                                    start, end, 0);
                        } else if (i == matchedIndex) {
                            Object span = buildActiveWordSpan();
                            if (span != null) {
                                builder.setSpan(span, start, end, 0);
                            }
                            if (selectedStyle != CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT
                                    && selectedStyle != CaptionStyleOptions.CaptionStyleType.KARAOKE_FLOW) {
                                builder.setSpan(
                                        new android.text.style.StyleSpan(
                                                android.graphics.Typeface.BOLD),
                                        start, end, 0);
                            }
                        } else {
                            builder.setSpan(
                                    new android.text.style.ForegroundColorSpan(restWordColor()),
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
                    int vw = mp.getVideoWidth();
                    int vh = mp.getVideoHeight();
                    if (vw > 0 && vh > 0) {
                        videoPreviewContainer.setAspectRatio(vw, vh);
                    }
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

        if (extractedWavFile != null) {
            extractedWavFile.delete();
        }

        SpeechToText.releaseModel();
    }
}
