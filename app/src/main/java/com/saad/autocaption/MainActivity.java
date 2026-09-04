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
import android.view.View;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    private TextView wordSlotBefore;
    private TextView wordSlotActive;
    private TextView wordSlotAfter;
    private Button generateCaptionsButton;
    private Button exportButton;
    private Button boxColorButton;
    private Button editWordColorsButton;
    private Spinner fontStyleSpinner;
    private Spinner captionColorSpinner;
    private Spinner captionStyleSpinner;
    private AspectRatioFrameLayout videoPreviewContainer;
    private Uri videoUri;
    private File extractedWavFile;
    private List<Caption> captions;

    // NEW: fixed-size caption blocks mirroring what AssSubtitleBuilder
    // uses for export, so preview and exported video always match and
    // captions never appear to type out one word at a time.
    private List<CaptionGrouper.Group> captionGroups;
    private static final int CAPTION_GROUP_SIZE = CaptionGrouper.DEFAULT_GROUP_SIZE;

    private Handler captionUpdateHandler;
    private Runnable captionUpdateRunnable;

    private SlotGestureHelper beforeSlotGesture;
    private SlotGestureHelper activeSlotGesture;
    private SlotGestureHelper afterSlotGesture;

    private Typeface selectedTypeface = Typeface.SANS_SERIF;
    private CaptionStyleOptions.FontOption selectedFontOption;
    private int selectedColor = 0xFFFFEB3B;

    // NEW: independent box background color for the Box Highlight
    // style. Defaults to a semi-transparent black pill.
    private int selectedBoxColor = 0xCC000000;

    private final float selectedFontSizeSp = 22f;

    private CaptionStyleOptions.CaptionStyleType selectedStyle =
            CaptionStyleOptions.CaptionStyleType.HIGHLIGHT_POP;

    private final AtomicInteger requestIdGenerator = new AtomicInteger(0);
    private volatile int currentRequestId = 0;

    private ActivityResultLauncher<Intent> pickVideoLauncher;
    private ActivityResultLauncher<String> storagePermissionLauncher;

    /** Callback used by both color-picker dialogs to hand back the
     * color the user picked once they tap Apply. */
    private interface ColorPickCallback {
        void onColorPicked(int color);
    }

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
        videoPreviewContainer = (AspectRatioFrameLayout) findViewById(R.id.videoPreviewContainer);
        wordSlotBefore = (TextView) findViewById(R.id.wordSlotBefore);
        wordSlotActive = (TextView) findViewById(R.id.wordSlotActive);
        wordSlotAfter = (TextView) findViewById(R.id.wordSlotAfter);
        Button selectVideoButton = (Button) findViewById(R.id.selectVideoButton);
        generateCaptionsButton = (Button) findViewById(R.id.generateCaptionsButton);
        exportButton = (Button) findViewById(R.id.exportButton);
        boxColorButton = (Button) findViewById(R.id.boxColorButton);
        editWordColorsButton = (Button) findViewById(R.id.editWordColorsButton);
        statusText = (TextView) findViewById(R.id.statusText);
        fontStyleSpinner = (Spinner) findViewById(R.id.fontStyleSpinner);
        captionColorSpinner = (Spinner) findViewById(R.id.captionColorSpinner);
        captionStyleSpinner = (Spinner) findViewById(R.id.captionStyleSpinner);

        wordSlotBefore.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        wordSlotActive.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        wordSlotAfter.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        wordSlotBefore.setTypeface(selectedTypeface);
        wordSlotActive.setTypeface(selectedTypeface);
        wordSlotAfter.setTypeface(selectedTypeface);

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

        boxColorButton.setOnClickListener(v ->
                showBoxColorPickerDialog(selectedBoxColor, color -> selectedBoxColor = color));

        editWordColorsButton.setOnClickListener(v -> showWordColorEditorDialog());

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
                    captionGroups = null;
                    wordSlotBefore.setText("");
                    wordSlotActive.setText("");
                    wordSlotAfter.setText("");
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
                        selectedFontOption,
                        selectedFontSizeSp,
                        selectedColor,
                        selectedBoxColor,
                        selectedStyle,
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
            }
        });
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
                wordSlotBefore.setTypeface(selectedTypeface);
                wordSlotActive.setTypeface(selectedTypeface);
                wordSlotAfter.setTypeface(selectedTypeface);
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
                    showHueColorPickerDialog(
                            "Pick Caption Color", selectedColor, color -> selectedColor = color);
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

    // ---------------------------------------------------------------
    // Color pickers
    // ---------------------------------------------------------------

    private int colorToHue(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return Math.round(hsv[0]);
    }

    /** Hue-only picker: saturation and brightness are locked to 100%,
     * so the user can never accidentally pick a washed-out/dull color.
     * Used for the global highlight color and every per-word override —
     * this is what guarantees highlight colors stay sharp and bright. */
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

    /** Box-background picker: unlike the highlight picker this allows
     * dark/desaturated shades and transparency, since a caption box
     * background is usually black/dark rather than a bright accent
     * color. Hue + Brightness + Opacity are all user-controlled. */
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

    /** Lists every recognized word with its current highlight swatch;
     * tapping a swatch opens the hue picker scoped to that single
     * Caption, setting Caption.customColor so each word's color can be
     * changed completely independently of the others. */
    private void showWordColorEditorDialog() {
        if (captions == null || captions.isEmpty()) {
            statusText.setText("Generate captions first");
            return;
        }

        ScrollView scrollView = new ScrollView(this);
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dpToPx(16);
        listContainer.setPadding(pad, pad, pad, pad);
        scrollView.addView(listContainer);

        for (Caption cap : captions) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) dpToPx(6), 0, (int) dpToPx(6));

            TextView wordLabel = new TextView(this);
            wordLabel.setText(cap.word);
            wordLabel.setTextColor(0xFFFFFFFF);
            wordLabel.setTextSize(16f);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            wordLabel.setLayoutParams(labelParams);

            Button swatch = new Button(this);
            swatch.setText("");
            LinearLayout.LayoutParams swatchParams =
                    new LinearLayout.LayoutParams((int) dpToPx(56), (int) dpToPx(32));
            swatch.setLayoutParams(swatchParams);
            swatch.setBackgroundColor(cap.resolveColor(selectedColor));

            swatch.setOnClickListener(v -> showHueColorPickerDialog(
                    "Color for \"" + cap.word + "\"",
                    cap.resolveColor(selectedColor),
                    chosen -> {
                        cap.customColor = chosen;
                        swatch.setBackgroundColor(chosen);
                    }));

            row.addView(wordLabel);
            row.addView(swatch);
            listContainer.addView(row);
        }

        new AlertDialog.Builder(this)
                .setTitle("Edit Word Colors")
                .setView(scrollView)
                .setPositiveButton("Done", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // ---------------------------------------------------------------
    // Video / caption pipeline
    // ---------------------------------------------------------------

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
                                captionGroups = CaptionGrouper.group(captions, CAPTION_GROUP_SIZE);
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

    // ---------------------------------------------------------------
    // Caption rendering
    // ---------------------------------------------------------------

    private Object buildActiveWordSpan(int color) {
        switch (selectedStyle) {
            case HIGHLIGHT_POP:
                // Fixed sharp/bright accent — kept in sync with
                // AssSubtitleBuilder.HIGHLIGHT_POP_COLOR for export parity.
                return new android.text.style.ForegroundColorSpan(0xFFFF3D00);

            case GREEN_EMPHASIS:
                // Kept in sync with AssSubtitleBuilder.GREEN_EMPHASIS_COLOR.
                return new android.text.style.ForegroundColorSpan(0xFF00E676);

            case KARAOKE_FLOW:
                return new KaraokeFillSpan(0xFFFFFFFF, color, 8f);

            case ONE_WORD_PUNCH:
                return new PopScaleSpan(color, 1.8f);

            case BOX_HIGHLIGHT:
                // Text color is this word's resolved highlight color;
                // box background is the user's independently-chosen
                // selectedBoxColor.
                return new BackgroundBoxSpan(color, selectedBoxColor, 12f, 16f);

            case BOUNCE:
                return new BounceSpan(color);

            case GLOW_POP:
                int glowColor = (color & 0x00FFFFFF) | 0x80000000;
                return new GlowPopSpan(color, glowColor, 1.15f, 5f);

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

    private void applySlotStyle(TextView slot, Caption caption, boolean isActive) {
        if (caption == null || caption.word == null || caption.word.isEmpty()) {
            slot.setText("");
            return;
        }
        String word = caption.word;
        int effectiveColor = caption.resolveColor(selectedColor);

        if (selectedStyle == CaptionStyleOptions.CaptionStyleType.MINIMAL_CLEAN) {
            slot.setText(word);
            slot.setTextColor(0xFFFFFFFF);
            slot.setTypeface(selectedTypeface, Typeface.BOLD);
            return;
        }

        if (!isActive) {
            slot.setText(word);
            slot.setTextColor(restWordColor());
            slot.setTypeface(selectedTypeface, Typeface.NORMAL);
            return;
        }

        android.text.SpannableString spannable = new android.text.SpannableString(word);
        Object span = buildActiveWordSpan(effectiveColor);
        if (span != null) {
            spannable.setSpan(span, 0, word.length(), 0);
        }
        boolean skipBold = selectedStyle == CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT
                || selectedStyle == CaptionStyleOptions.CaptionStyleType.KARAOKE_FLOW;
        slot.setTypeface(selectedTypeface, skipBold ? Typeface.NORMAL : Typeface.BOLD);
        slot.setText(spannable);
    }

    // Measures how wide a slot's current text actually renders,
    // including its own left/right padding.
    private float measureSlotWidth(TextView slot) {
        CharSequence text = slot.getText();
        if (text == null || text.length() == 0) {
            return 0f;
        }
        return slot.getPaint().measureText(text, 0, text.length())
                + slot.getPaddingLeft() + slot.getPaddingRight();
    }

    // Places before/active/after slots side-by-side based on their
    // actual measured width, with a small fixed gap — only for slots the
    // user hasn't manually dragged. Prevents overlap on long words and
    // excess empty gap on short words.
    private void autoSpaceSlots() {
        float gap = dpToPx(8);

        float activeWidth = measureSlotWidth(wordSlotActive);

        if (!activeSlotGesture.isPositionDragged()) {
            wordSlotActive.setTranslationX(0f);
        }

        if (!beforeSlotGesture.isPositionDragged()) {
            float beforeWidth = measureSlotWidth(wordSlotBefore);
            wordSlotBefore.setTranslationX(-(activeWidth / 2f + gap + beforeWidth / 2f));
        }

        if (!afterSlotGesture.isPositionDragged()) {
            float afterWidth = measureSlotWidth(wordSlotAfter);
            wordSlotAfter.setTranslationX(activeWidth / 2f + gap + afterWidth / 2f);
        }
    }

    // FIX: uses fixed 3-word CaptionGrouper blocks instead of a sliding
    // window recomputed from the globally active word. The three slots
    // now map 1:1 to a stable group's word[0]/word[1]/word[2]; only
    // which slot is "active" (highlighted) changes as playback moves
    // through the group. This is what stops captions from appearing to
    // type out one word at a time.
    private void startCaptionUpdates() {
        captionUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()
                        && captionGroups != null && !captionGroups.isEmpty()) {

                    float currentTimeSec = mediaPlayer.getCurrentPosition() / 1000.0f;
                    int groupIndex = CaptionGrouper.groupIndexAt(captionGroups, currentTimeSec);

                    if (groupIndex != -1) {
                        CaptionGrouper.Group group = captionGroups.get(groupIndex);
                        int activeIndex = group.nearestIndexAt(currentTimeSec);

                        boolean oneWordPunch = selectedStyle
                                == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH;

                        if (oneWordPunch) {
                            wordSlotBefore.setText("");
                            applySlotStyle(wordSlotActive, group.words.get(activeIndex), true);
                            wordSlotAfter.setText("");
                        } else {
                            TextView[] slots = { wordSlotBefore, wordSlotActive, wordSlotAfter };
                            for (int slotPos = 0; slotPos < slots.length; slotPos++) {
                                if (slotPos >= group.words.size()) {
                                    slots[slotPos].setText("");
                                } else {
                                    applySlotStyle(slots[slotPos], group.words.get(slotPos),
                                            slotPos == activeIndex);
                                }
                            }
                        }

                        autoSpaceSlots();
                    }
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
