package com.saad.autocaption;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private SurfaceView videoSurface;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private TextView statusText;
    private TextView wordSlotBefore;
    private TextView wordSlotActive;
    private TextView wordSlotAfter;
    private LinearLayout captionLayer;
    private Button generateCaptionsButton;
    private Button exportButton;
    private Button boxColorButton;
    private Spinner fontStyleSpinner;
    private Spinner captionColorSpinner;
    private Spinner captionStyleSpinner;
    private AspectRatioFrameLayout videoPreviewContainer;

    private Button playPauseButton;
    private SeekBar videoSeekBar;
    private TextView timeText;
    private boolean isTrackingTouch = false;

    // Slot Selection Controls
    private Button btnSlotBefore;
    private Button btnSlotActive;
    private Button btnSlotAfter;

    private SlotStyleConfig configSlotBefore;
    private SlotStyleConfig configSlotActive;
    private SlotStyleConfig configSlotAfter;
    private int currentSelectedSlotIndex = 1; // 0 = Before, 1 = Active, 2 = After
    private boolean isUpdatingSpinnersProgrammatically = false;

    private Uri videoUri;
    private File extractedWavFile;
    private List<Caption> captions;
    private List<CaptionGrouper.Group> captionGroups;
    private static final int CAPTION_GROUP_SIZE = CaptionGrouper.DEFAULT_GROUP_SIZE;

    private Handler captionUpdateHandler;
    private Runnable captionUpdateRunnable;

    private SlotGestureHelper beforeSlotGesture;
    private SlotGestureHelper activeSlotGesture;
    private SlotGestureHelper afterSlotGesture;

    private final float selectedFontSizeSp = 22f;

    private final AtomicInteger requestIdGenerator = new AtomicInteger(0);
    private volatile int currentRequestId = 0;

    private ActivityResultLauncher<Intent> pickVideoLauncher;
    private ActivityResultLauncher<String> storagePermissionLauncher;

    private interface ColorPickCallback {
        void onColorPicked(int color);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        CaptionStyleOptions.FontOption defaultFont = CaptionStyleOptions.getFontOptions()[0];
        Typeface defaultTf = CaptionStyleOptions.resolveTypeface(this, defaultFont);

        configSlotBefore = new SlotStyleConfig(defaultFont, defaultTf, 0xFFCCCCCC,
                CaptionStyleOptions.CaptionStyleType.MINIMAL_CLEAN, 0xCC000000);
        configSlotActive = new SlotStyleConfig(defaultFont, defaultTf, 0xFFFFEA00,
                CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT, 0xCC000000);
        configSlotAfter = new SlotStyleConfig(defaultFont, defaultTf, 0xFFCCCCCC,
                CaptionStyleOptions.CaptionStyleType.MINIMAL_CLEAN, 0xCC000000);

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
                        statusText.setText("Storage permission denied — export may fail");
                    }
                });

        requestStoragePermissionIfNeeded();

        videoSurface = findViewById(R.id.videoSurface);
        videoPreviewContainer = findViewById(R.id.videoPreviewContainer);
        captionLayer = findViewById(R.id.captionLayer);
        wordSlotBefore = findViewById(R.id.wordSlotBefore);
        wordSlotActive = findViewById(R.id.wordSlotActive);
        wordSlotAfter = findViewById(R.id.wordSlotAfter);
        Button selectVideoButton = findViewById(R.id.selectVideoButton);
        generateCaptionsButton = findViewById(R.id.generateCaptionsButton);
        exportButton = findViewById(R.id.exportButton);
        boxColorButton = findViewById(R.id.boxColorButton);
        statusText = findViewById(R.id.statusText);
        fontStyleSpinner = findViewById(R.id.fontStyleSpinner);
        captionColorSpinner = findViewById(R.id.captionColorSpinner);
        captionStyleSpinner = findViewById(R.id.captionStyleSpinner);

        playPauseButton = findViewById(R.id.playPauseButton);
        videoSeekBar = findViewById(R.id.videoSeekBar);
        timeText = findViewById(R.id.timeText);

        btnSlotBefore = findViewById(R.id.btnSlotBefore);
        btnSlotActive = findViewById(R.id.btnSlotActive);
        btnSlotAfter = findViewById(R.id.btnSlotAfter);

        btnSlotBefore.setOnClickListener(v -> selectSlotTab(0));
        btnSlotActive.setOnClickListener(v -> selectSlotTab(1));
        btnSlotAfter.setOnClickListener(v -> selectSlotTab(2));

        wordSlotBefore.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        wordSlotActive.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        wordSlotAfter.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        wordSlotBefore.setTextSize(TypedValue.COMPLEX_UNIT_SP, selectedFontSizeSp);
        wordSlotActive.setTextSize(TypedValue.COMPLEX_UNIT_SP, selectedFontSizeSp);
        wordSlotAfter.setTextSize(TypedValue.COMPLEX_UNIT_SP, selectedFontSizeSp);

        beforeSlotGesture = new SlotGestureHelper(this);
        wordSlotBefore.setOnTouchListener(beforeSlotGesture);

        activeSlotGesture = new SlotGestureHelper(this);
        wordSlotActive.setOnTouchListener(activeSlotGesture);

        afterSlotGesture = new SlotGestureHelper(this);
        wordSlotAfter.setOnTouchListener(afterSlotGesture);

        generateCaptionsButton.setEnabled(false);
        exportButton.setEnabled(false);

        setupFontSpinner();
        setupColorSpinner();
        setupStyleSpinner();

        selectSlotTab(1);

        playPauseButton.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                        playPauseButton.setText("Play");
                    } else {
                        mediaPlayer.start();
                        playPauseButton.setText("Pause");
                    }
                } catch (Exception ignored) {}
            }
        });

        videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    try { mediaPlayer.seekTo(progress); } catch (Exception ignored) {}
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { isTrackingTouch = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { isTrackingTouch = false; }
        });

        boxColorButton.setOnClickListener(v -> {
            SlotStyleConfig current = getCurrentSlotConfig();
            showBoxColorPickerDialog(current.boxColor, color -> {
                current.boxColor = color;
                triggerManualCaptionRedraw();
            });
        });

        captionUpdateHandler = new Handler(Looper.getMainLooper());

        surfaceHolder = videoSurface.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSPARENT);
        videoSurface.setZOrderOnTop(false);
        videoSurface.setZOrderMediaOverlay(false);

        captionLayer.bringToFront();

        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (videoUri != null) {
                    playVideo(videoUri);
                }
            }

            @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopCaptionUpdates();
                if (mediaPlayer != null) {
                    try { mediaPlayer.release(); } catch (Exception ignored) {}
                    mediaPlayer = null;
                }
            }
        });

        selectVideoButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pickVideoLauncher.launch(intent);
        });

        generateCaptionsButton.setOnClickListener(v -> {
            if (videoUri != null) {
                final int requestId = currentRequestId;
                generateCaptionsButton.setEnabled(false);
                exportButton.setEnabled(false);
                captions = null;
                captionGroups = null;
                wordSlotBefore.setText("");
                wordSlotActive.setText("");
                wordSlotAfter.setText("");
                extractAudio(videoUri, requestId);
            }
        });

        exportButton.setOnClickListener(v -> {
            if (captions == null || captions.isEmpty() || videoUri == null) {
                statusText.setText("Generate captions before exporting");
                return;
            }
            exportButton.setEnabled(false);
            generateCaptionsButton.setEnabled(false);

            int previewWidthPx = videoPreviewContainer.getWidth();
            int previewHeightPx = videoPreviewContainer.getHeight();

            CaptionSlotTransform beforeTransform = new CaptionSlotTransform(
                    wordSlotBefore.getTranslationX(), wordSlotBefore.getTranslationY(),
                    beforeSlotGesture.getScale());
            CaptionSlotTransform activeTransform = new CaptionSlotTransform(
                    wordSlotActive.getTranslationX(), wordSlotActive.getTranslationY(),
                    activeSlotGesture.getScale());
            CaptionSlotTransform afterTransform = new CaptionSlotTransform(
                    wordSlotAfter.getTranslationX(), wordSlotAfter.getTranslationY(),
                    afterSlotGesture.getScale());

            VideoExporter.export(
                    MainActivity.this,
                    videoUri,
                    captions,
                    configSlotBefore,
                    configSlotActive,
                    configSlotAfter,
                    selectedFontSizeSp,
                    previewWidthPx,
                    previewHeightPx,
                    beforeTransform,
                    activeTransform,
                    afterTransform,
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
        });
    }

    private SlotStyleConfig getCurrentSlotConfig() {
        if (currentSelectedSlotIndex == 0) return configSlotBefore;
        if (currentSelectedSlotIndex == 2) return configSlotAfter;
        return configSlotActive;
    }

    private void selectSlotTab(int slotIndex) {
        currentSelectedSlotIndex = slotIndex;

        btnSlotBefore.setBackgroundColor(slotIndex == 0 ? 0xFF00E676 : 0xFF2C2C2C);
        btnSlotBefore.setTextColor(slotIndex == 0 ? 0xFF000000 : 0xFFAAAAAA);

        btnSlotActive.setBackgroundColor(slotIndex == 1 ? 0xFF00E676 : 0xFF2C2C2C);
        btnSlotActive.setTextColor(slotIndex == 1 ? 0xFF000000 : 0xFFAAAAAA);

        btnSlotAfter.setBackgroundColor(slotIndex == 2 ? 0xFF00E676 : 0xFF2C2C2C);
        btnSlotAfter.setTextColor(slotIndex == 2 ? 0xFF000000 : 0xFFAAAAAA);

        syncSpinnersWithCurrentConfig();
    }

    private void syncSpinnersWithCurrentConfig() {
        isUpdatingSpinnersProgrammatically = true;
        SlotStyleConfig current = getCurrentSlotConfig();

        CaptionStyleOptions.FontOption[] fonts = CaptionStyleOptions.getFontOptions();
        for (int i = 0; i < fonts.length; i++) {
            if (fonts[i].label.equals(current.fontOption.label)) {
                fontStyleSpinner.setSelection(i);
                break;
            }
        }

        CaptionStyleOptions.StyleOption[] styles = CaptionStyleOptions.getStyleOptions();
        for (int i = 0; i < styles.length; i++) {
            if (styles[i].type == current.styleType) {
                captionStyleSpinner.setSelection(i);
                break;
            }
        }

        CaptionStyleOptions.ColorOption[] colors = CaptionStyleOptions.getColorOptions();
        boolean matched = false;
        for (int i = 0; i < colors.length; i++) {
            if (colors[i].color != 0 && colors[i].color == current.textColor) {
                captionColorSpinner.setSelection(i);
                matched = true;
                break;
            }
        }
        if (!matched) {
            captionColorSpinner.setSelection(colors.length - 1);
        }

        isUpdatingSpinnersProgrammatically = false;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            boolean granted = ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
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
                if (isUpdatingSpinnersProgrammatically) return;
                SlotStyleConfig current = getCurrentSlotConfig();
                current.fontOption = fonts[position];
                current.typeface = CaptionStyleOptions.resolveTypeface(MainActivity.this, current.fontOption);
                triggerManualCaptionRedraw();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
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
                if (isUpdatingSpinnersProgrammatically) return;
                CaptionStyleOptions.ColorOption chosen = colors[position];
                SlotStyleConfig current = getCurrentSlotConfig();
                if (chosen.color == 0) {
                    showHueColorPickerDialog(
                            "Pick Caption Color", current.textColor, color -> {
                                current.textColor = color;
                                triggerManualCaptionRedraw();
                            });
                } else {
                    current.textColor = chosen.color;
                    triggerManualCaptionRedraw();
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
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
                if (isUpdatingSpinnersProgrammatically) return;
                getCurrentSlotConfig().styleType = styles[position].type;
                triggerManualCaptionRedraw();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private int colorToHue(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return Math.round(hsv[0]);
    }

    private void showHueColorPickerDialog(String title, int initialColor, ColorPickCallback callback) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dpToPx(20);
        container.setPadding(pad, pad, pad, pad);

        final View previewBox = new View(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) dpToPx(60));
        previewParams.bottomMargin = (int) dpToPx(16);
        previewBox.setLayoutParams(previewParams);

        final int[] hue = { colorToHue(initialColor) };
        previewBox.setBackgroundColor(Color.HSVToColor(new float[]{hue[0], 1f, 1f}));

        SeekBar hueSlider = new SeekBar(this);
        hueSlider.setMax(360);
        hueSlider.setProgress(hue[0]);
        hueSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hue[0] = progress;
                previewBox.setBackgroundColor(Color.HSVToColor(new float[]{progress, 1f, 1f}));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        container.addView(previewBox);
        container.addView(hueSlider);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("Apply", (dialog, which) ->
                        callback.onColorPicked(Color.HSVToColor(new float[]{hue[0], 1f, 1f})))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showBoxColorPickerDialog(int initialColor, ColorPickCallback callback) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dpToPx(20);
        container.setPadding(pad, pad, pad, pad);

        final View previewBox = new View(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) dpToPx(60));
        previewParams.bottomMargin = (int) dpToPx(16);
        previewBox.setLayoutParams(previewParams);

        float[] initHsv = new float[3];
        Color.colorToHSV(initialColor, initHsv);
        final int[] hue = { Math.round(initHsv[0]) };
        final int[] value = { Math.round(initHsv[2] * 100) };
        final int[] alpha = { Math.round(Color.alpha(initialColor) * 100 / 255f) };

        Runnable updatePreview = () -> previewBox.setBackgroundColor(
                Color.HSVToColor(Math.round(alpha[0] * 255 / 100f),
                        new float[]{hue[0], 1f, value[0] / 100f}));
        updatePreview.run();

        TextView hueLabel = new TextView(this);
        hueLabel.setText("Hue");
        hueLabel.setTextColor(0xFFCCCCCC);
        SeekBar hueSlider = new SeekBar(this);
        hueSlider.setMax(360);
        hueSlider.setProgress(hue[0]);

        TextView valueLabel = new TextView(this);
        valueLabel.setText("Brightness");
        valueLabel.setTextColor(0xFFCCCCCC);
        SeekBar valueSlider = new SeekBar(this);
        valueSlider.setMax(100);
        valueSlider.setProgress(value[0]);

        TextView alphaLabel = new TextView(this);
        alphaLabel.setText("Opacity");
        alphaLabel.setTextColor(0xFFCCCCCC);
        SeekBar alphaSlider = new SeekBar(this);
        alphaSlider.setMax(100);
        alphaSlider.setProgress(alpha[0]);

        hueSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                hue[0] = p; updatePreview.run();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        valueSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                value[0] = p; updatePreview.run();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        alphaSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                alpha[0] = p; updatePreview.run();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        container.addView(previewBox);
        container.addView(hueLabel);
        container.addView(hueSlider);
        container.addView(valueLabel);
        container.addView(valueSlider);
        container.addView(alphaLabel);
        container.addView(alphaSlider);

        new AlertDialog.Builder(this)
                .setTitle("Box Background Color")
                .setView(container)
                .setPositiveButton("Apply", (dialog, which) ->
                        callback.onColorPicked(Color.HSVToColor(
                                Math.round(alpha[0] * 255 / 100f),
                                new float[]{hue[0], 1f, value[0] / 100f})))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void handlePickedVideo(Intent data) {
        videoUri = data.getData();
        if (videoUri != null) {
            try {
                getContentResolver().takePersistableUriPermission(
                        videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {}
        }

        currentRequestId = requestIdGenerator.incrementAndGet();

        captions = null;
        captionGroups = null;
        wordSlotBefore.setText("");
        wordSlotActive.setText("");
        wordSlotAfter.setText("");
        exportButton.setEnabled(false);

        playVideo(videoUri);
        statusText.setText("Video loaded. Tap 'Generate Captions' to continue.");
        generateCaptionsButton.setEnabled(true);
    }

    private void extractAudio(Uri uri, int requestId) {
        statusText.setText("Extracting audio with FFmpeg...");

        AudioExtractor.extractAudioToWav(
                this,
                uri,
                new AudioExtractor.ExtractCallback() {
                    @Override
                    public void onSuccess(File wavFile) {
                        runOnUiThread(() -> {
                            if (requestId != currentRequestId) {
                                wavFile.delete();
                                return;
                            }
                            if (extractedWavFile != null && !extractedWavFile.equals(wavFile)) {
                                extractedWavFile.delete();
                            }
                            extractedWavFile = wavFile;
                            setupModelAndRecognize(wavFile, requestId);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (requestId != currentRequestId) return;
                            statusText.setText(message);
                            generateCaptionsButton.setEnabled(true);
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
                        runOnUiThread(() -> {
                            if (requestId != currentRequestId) return;
                            statusText.setText(message);
                        });
                    }

                    @Override
                    public void onSuccess(File modelDir) {
                        runOnUiThread(() -> {
                            if (requestId != currentRequestId) return;
                            runSpeechRecognition(modelDir, wavFile, requestId);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (requestId != currentRequestId) return;
                            statusText.setText(message);
                            generateCaptionsButton.setEnabled(true);
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
                        runOnUiThread(() -> {
                            if (requestId != currentRequestId) return;
                            statusText.setText(message);
                        });
                    }

                    @Override
                    public void onSuccess(List<String> jsonResults) {
                        runOnUiThread(() -> {
                            if (requestId != currentRequestId) return;
                            try {
                                List<Caption> parsed = CaptionParser.parseVoskResults(jsonResults);
                                if (parsed == null || parsed.isEmpty()) {
                                    statusText.setText("No speech detected in video.");
                                    generateCaptionsButton.setEnabled(true);
                                    return;
                                }

                                captions = parsed;
                                captionGroups = CaptionGrouper.group(captions, CAPTION_GROUP_SIZE);
                                statusText.setText("Captions ready! (" + captions.size() + " words)");
                                generateCaptionsButton.setEnabled(true);
                                exportButton.setEnabled(true);

                                if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                                    mediaPlayer.start();
                                    playPauseButton.setText("Pause");
                                }

                                captionLayer.bringToFront();
                                startCaptionUpdates();

                            } catch (Throwable t) {
                                Log.e(TAG, "Error finalizing captions", t);
                                statusText.setText("Parsing error: " + t.getMessage());
                                generateCaptionsButton.setEnabled(true);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (requestId != currentRequestId) return;
                            statusText.setText(message);
                            generateCaptionsButton.setEnabled(true);
                        });
                    }
                });
    }

    private void applySlotStyle(TextView slot, Caption caption, SlotStyleConfig config, boolean isActive) {
        if (slot == null) return;

        if (caption == null || caption.word == null || caption.word.trim().isEmpty()) {
            slot.setText("");
            return;
        }

        String word = caption.word;
        int effectiveColor = caption.resolveColor(config.textColor);

        slot.setTypeface(config.typeface);

        if (config.styleType == CaptionStyleOptions.CaptionStyleType.MINIMAL_CLEAN) {
            slot.setText(word);
            slot.setTextColor(effectiveColor);
            slot.setTypeface(config.typeface, isActive ? Typeface.BOLD : Typeface.NORMAL);
            return;
        }

        android.text.SpannableString spannable = new android.text.SpannableString(word);
        Object span = null;

        switch (config.styleType) {
            case HIGHLIGHT_POP:
                span = new android.text.style.ForegroundColorSpan(effectiveColor);
                break;
            case GREEN_EMPHASIS:
                span = new android.text.style.ForegroundColorSpan(0xFF00E676);
                break;
            case ONE_WORD_PUNCH:
                span = new PopScaleSpan(effectiveColor, 1.4f);
                break;
            case BOX_HIGHLIGHT:
                span = new BackgroundBoxSpan(effectiveColor, config.boxColor, 12f, 10f);
                break;
            case BOUNCE:
                span = new BounceSpan(effectiveColor);
                break;
            case GLOW_POP:
                int neonGlow = (effectiveColor & 0x00FFFFFF) | 0xDD000000;
                span = new GlowPopSpan(effectiveColor, neonGlow, 1.15f, 10f);
                break;
            default:
                break;
        }

        if (span != null) {
            spannable.setSpan(span, 0, word.length(), 0);
        }

        boolean skipBold = config.styleType == CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT;
        slot.setTypeface(config.typeface, skipBold ? Typeface.NORMAL : Typeface.BOLD);
        slot.setText(spannable);
    }

        private void renderGroupSafe(CaptionGrouper.Group group, int activeIndex) {
        if (group == null || group.words == null || group.words.isEmpty()) {
            wordSlotBefore.setText("");
            wordSlotActive.setText("");
            wordSlotAfter.setText("");
            return;
        }

        boolean oneWordPunch = configSlotActive.styleType == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH;
        int safeActive = (activeIndex >= 0 && activeIndex < group.words.size()) ? activeIndex : 0;

        if (oneWordPunch) {
            wordSlotBefore.setText("");
            applySlotStyle(wordSlotActive, group.words.get(safeActive), configSlotActive, true);
            wordSlotAfter.setText("");
        } else {
            // Dynamic sliding window: active word center slot mein highlight hoga[span_0](start_span)[span_0](end_span)[span_1](start_span)[span_1](end_span)
            Caption capBefore = (safeActive - 1 >= 0) ? group.words.get(safeActive - 1) : null;
            Caption capActive = group.words.get(safeActive);
            Caption capAfter = (safeActive + 1 < group.words.size()) ? group.words.get(safeActive + 1) : null;

            applySlotStyle(wordSlotBefore, capBefore, configSlotBefore, false);
            applySlotStyle(wordSlotActive, capActive, configSlotActive, true);
            applySlotStyle(wordSlotAfter, capAfter, configSlotAfter, false);
        }
    }


    private void triggerManualCaptionRedraw() {
        if (mediaPlayer != null && captionGroups != null && !captionGroups.isEmpty()) {
            try {
                float currentTimeSec = mediaPlayer.getCurrentPosition() / 1000.0f;
                int groupIndex = CaptionGrouper.groupIndexAt(captionGroups, currentTimeSec);
                if (groupIndex != -1 && groupIndex < captionGroups.size()) {
                    CaptionGrouper.Group group = captionGroups.get(groupIndex);
                    int activeIndex = group.nearestIndexAt(currentTimeSec);
                    renderGroupSafe(group, activeIndex);
                }
            } catch (Exception ignored) {}
        }
    }

    private void startCaptionUpdates() {
        stopCaptionUpdates();

        captionUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (mediaPlayer != null) {
                        int currentPos = mediaPlayer.getCurrentPosition();
                        int duration = mediaPlayer.getDuration();

                        if (!isTrackingTouch && duration > 0) {
                            videoSeekBar.setMax(duration);
                            videoSeekBar.setProgress(currentPos);
                            int s = currentPos / 1000;
                            timeText.setText(String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60));
                        }

                        if (mediaPlayer.isPlaying() && captionGroups != null && !captionGroups.isEmpty()) {
                            float currentTimeSec = currentPos / 1000.0f;
                            int groupIndex = CaptionGrouper.groupIndexAt(captionGroups, currentTimeSec);

                            if (groupIndex != -1 && groupIndex < captionGroups.size()) {
                                CaptionGrouper.Group group = captionGroups.get(groupIndex);
                                int activeIndex = group.nearestIndexAt(currentTimeSec);
                                renderGroupSafe(group, activeIndex);
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Caption update loop caught exception", t);
                }
                captionUpdateHandler.postDelayed(this, 50);
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

            mediaPlayer.setOnPreparedListener(mp -> {
                int vw = mp.getVideoWidth();
                int vh = mp.getVideoHeight();
                if (vw > 0 && vh > 0) {
                    videoPreviewContainer.setAspectRatio(vw, vh);
                }
                videoSeekBar.setMax(mp.getDuration());
                playPauseButton.setText("Pause");
                mp.setLooping(true);
                mp.start();
                captionLayer.bringToFront();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                statusText.setText("Playback error");
                return true;
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
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (extractedWavFile != null) {
            extractedWavFile.delete();
        }
        SpeechToText.releaseModel();
    }
}
