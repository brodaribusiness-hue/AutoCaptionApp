package com.saad.autocaption;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
    private TextView wordSlotBefore;
    private TextView wordSlotActive;
    private TextView wordSlotAfter;
    private View highlightBoxView;
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

    private SlotGestureHelper beforeSlotGesture;
    private SlotGestureHelper activeSlotGesture;
    private SlotGestureHelper afterSlotGesture;

    private Typeface selectedTypeface = Typeface.SANS_SERIF;
    private CaptionStyleOptions.FontOption selectedFontOption;
    private int selectedColor = 0xFFFFEB3B;
    private final float selectedFontSizeSp = 22f;

    private CaptionStyleOptions.CaptionStyleType selectedStyle =
            CaptionStyleOptions.CaptionStyleType.HIGHLIGHT_POP;

    private final AtomicInteger requestIdGenerator = new AtomicInteger(0);
    private volatile int currentRequestId = 0;

    // Tracks which caption index is currently shown, so the sync loop
    // only touches views on a genuine word change (no flicker, and lets
    // per-word transition animations run exactly once per word).
    private int lastAnchorIndex = -1;
    private ValueAnimator boxSlideAnimator;

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
        videoPreviewContainer = (AspectRatioFrameLayout) findViewById(R.id.videoPreviewContainer);
        wordSlotBefore = (TextView) findViewById(R.id.wordSlotBefore);
        wordSlotActive = (TextView) findViewById(R.id.wordSlotActive);
        wordSlotAfter = (TextView) findViewById(R.id.wordSlotAfter);
        Button selectVideoButton = (Button) findViewById(R.id.selectVideoButton);
        generateCaptionsButton = (Button) findViewById(R.id.generateCaptionsButton);
        exportButton = (Button) findViewById(R.id.exportButton);
        statusText = (TextView) findViewById(R.id.statusText);
        fontStyleSpinner = (Spinner) findViewById(R.id.fontStyleSpinner);
        captionColorSpinner = (Spinner) findViewById(R.id.captionColorSpinner);
        captionStyleSpinner = (Spinner) findViewById(R.id.captionStyleSpinner);

        // Only GLOW_POP's BlurMaskFilter needs a software layer. Forcing
        // it on every slot baked text into a bitmap that then got
        // stretched by pinch-zoom scale — that's what was blurring text
        // and washing out color-only style differences (Highlight Pop,
        // Green Emphasis, Karaoke Flow, Box Highlight all looked "broken"
        // because the blur made their color/shape differences invisible).
        wordSlotBefore.setLayerType(View.LAYER_TYPE_NONE, null);
        wordSlotActive.setLayerType(View.LAYER_TYPE_NONE, null);
        wordSlotAfter.setLayerType(View.LAYER_TYPE_NONE, null);

        wordSlotBefore.getPaint().setAntiAlias(true);
        wordSlotActive.getPaint().setAntiAlias(true);
        wordSlotAfter.getPaint().setAntiAlias(true);
        wordSlotBefore.getPaint().setSubpixelText(true);
        wordSlotActive.getPaint().setSubpixelText(true);
        wordSlotAfter.getPaint().setSubpixelText(true);

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
        setupHighlightBoxView();

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
                // FIX: caption loop previously died in surfaceDestroyed()
                // whenever the app backgrounded, and was never restarted
                // here — captions froze permanently until the user
                // regenerated them. Now every surface (re)creation
                // re-attaches the sync clock, no regeneration needed.
                resyncCaptionEngineIfNeeded();
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
                    lastAnchorIndex = -1;
                    wordSlotBefore.setText("");
                    wordSlotActive.setText("");
                    wordSlotAfter.setText("");
                    highlightBoxView.setAlpha(0f);
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
                // Style changed — hide any stale box highlight and force
                // a fresh render of the currently active word so the new
                // style's colors/effects apply immediately.
                highlightBoxView.animate().cancel();
                highlightBoxView.setAlpha(0f);
                lastAnchorIndex = -1;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // Creates the shared sliding background box used by BOX_HIGHLIGHT.
    // A single persistent View is animated between words instead of
    // being recreated per-word, which is what makes the transition a
    // smooth slide instead of a pop/flicker.
    private void setupHighlightBoxView() {
        FrameLayout captionLayer = findViewById(R.id.captionLayer);

        highlightBoxView = new View(this);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(0xFF000000);
        boxBg.setCornerRadius(dpToPx(12));
        highlightBoxView.setBackground(boxBg);
        highlightBoxView.setAlpha(0f);

        FrameLayout.LayoutParams boxParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        boxParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        // Index 0 so it draws behind wordSlotBefore/Active/After, which
        // are added to the same FrameLayout in the XML layout.
        captionLayer.addView(highlightBoxView, 0, boxParams);
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
        lastAnchorIndex = -1;
        wordSlotBefore.setText("");
        wordSlotActive.setText("");
        wordSlotAfter.setText("");
        highlightBoxView.setAlpha(0f);
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
                                lastAnchorIndex = -1;
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

    // Only GREEN_EMPHASIS, KARAOKE_FLOW, BOUNCE, ONE_WORD_PUNCH and
    // GLOW_POP use a plain span for their active-word color/effect.
    // HIGHLIGHT_POP, GREEN_EMPHASIS and BOX_HIGHLIGHT are drawn with a
    // stroked OutlineSpan instead (see applySlotStyle) for sharp,
    // high-contrast edges against any video background.
    private Object buildActiveWordSpan() {
        switch (selectedStyle) {
            case KARAOKE_FLOW:
                return new KaraokeFillSpan(0xFF000000, selectedColor, 10f);

            case ONE_WORD_PUNCH:
                return new PopScaleSpan(selectedColor, 1.8f);

            case BOUNCE:
                return new BounceSpan(selectedColor);

            case GLOW_POP:
                int glowColor = (selectedColor & 0x00FFFFFF) | 0x80000000;
                return new GlowPopSpan(selectedColor, glowColor, 1.15f, 5f);

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
                return 0xFFDDDDDD;
        }
    }

    // Only GLOW_POP's BlurMaskFilter requires a software layer. Every
    // other style stays hardware-accelerated (LAYER_TYPE_NONE) so text
    // is redrawn crisply at the current pinch-zoom scale instead of
    // stretching a cached bitmap, which was the source of the blur.
    private void applyLayerTypeForStyle(TextView slot, boolean isActive) {
        boolean needsSoftwareLayer =
                isActive && selectedStyle == CaptionStyleOptions.CaptionStyleType.GLOW_POP;
        int desired = needsSoftwareLayer ? View.LAYER_TYPE_SOFTWARE : View.LAYER_TYPE_NONE;
        if (slot.getLayerType() != desired) {
            slot.setLayerType(desired, null);
        }
    }

    private void applySlotStyle(TextView slot, String word, boolean isActive) {
        if (word == null || word.isEmpty()) {
            slot.setText("");
            return;
        }

        applyLayerTypeForStyle(slot, isActive);

        // Strong shadow on the TextView's own Paint. Spans draw using
        // this same Paint, so the shadow applies under every style —
        // including custom ReplacementSpans — giving sharp separation
        // from busy video backgrounds instead of a washed-out look.
        slot.setShadowLayer(6f, 0f, 2f, 0xFF000000);

        if (selectedStyle == CaptionStyleOptions.CaptionStyleType.MINIMAL_CLEAN) {
            slot.setText(word);
            slot.setTextColor(0xFFFFFFFF);
            slot.setTypeface(selectedTypeface, Typeface.BOLD);
            return;
        }

        if (!isActive) {
            android.text.SpannableString rest = new android.text.SpannableString(word);
            rest.setSpan(new OutlineSpan(restWordColor(), 0xFF000000, 3.5f),
                    0, word.length(), 0);
            slot.setTypeface(selectedTypeface, Typeface.NORMAL);
            slot.setText(rest);
            return;
        }

        android.text.SpannableString spannable = new android.text.SpannableString(word);

        boolean needsOutlineBase =
                selectedStyle == CaptionStyleOptions.CaptionStyleType.HIGHLIGHT_POP
                || selectedStyle == CaptionStyleOptions.CaptionStyleType.GREEN_EMPHASIS
                || selectedStyle == CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT;

        if (needsOutlineBase) {
            int fillColor;
            if (selectedStyle == CaptionStyleOptions.CaptionStyleType.GREEN_EMPHASIS) {
                fillColor = 0xFF39FF14; // vibrant neon green, not the old dull Material green
            } else if (selectedStyle == CaptionStyleOptions.CaptionStyleType.HIGHLIGHT_POP) {
                fillColor = 0xFFFF9800;
            } else {
                fillColor = 0xFFFFFFFF; // BOX_HIGHLIGHT: white text on the sliding dark box
            }
            spannable.setSpan(new OutlineSpan(fillColor, 0xFF000000, 4f), 0, word.length(), 0);
        } else {
            Object span = buildActiveWordSpan();
            if (span != null) {
                spannable.setSpan(span, 0, word.length(), 0);
            }
        }

        boolean skipBold = selectedStyle == CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT
                || selectedStyle == CaptionStyleOptions.CaptionStyleType.KARAOKE_FLOW;
        slot.setTypeface(selectedTypeface, skipBold ? Typeface.NORMAL : Typeface.BOLD);
        slot.setText(spannable);
    }

    private float measureSlotWidth(TextView slot) {
        CharSequence text = slot.getText();
        if (text == null || text.length() == 0) {
            return 0f;
        }
        return slot.getPaint().measureText(text, 0, text.length())
                + slot.getPaddingLeft() + slot.getPaddingRight();
    }

    // Places before/active/after slots side-by-side based on their
    // actual measured width, with a small fixed gap — only for slots
    // the user hasn't manually dragged. Prevents overlap on long words
    // and excess empty gap on short words.
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

    // Fires once per genuine word change (never on the 33ms poll tick
    // itself). Drives the Highlight Pop scale-spring and the Box
    // Highlight sliding background — a single persistent View animated
    // to the new word's position, not recreated per word, so the
    // transition is a real slide with no flicker.
    private void playActiveWordTransition() {
        if (selectedStyle == CaptionStyleOptions.CaptionStyleType.HIGHLIGHT_POP) {
            wordSlotActive.animate().cancel();
            wordSlotActive.setScaleX(1f);
            wordSlotActive.setScaleY(1f);
            wordSlotActive.animate()
                    .scaleX(1.15f).scaleY(1.15f)
                    .setDuration(90)
                    .setInterpolator(new OvershootInterpolator(3f))
                    .withEndAction(() -> wordSlotActive.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(110)
                            .setInterpolator(new DecelerateInterpolator())
                            .start())
                    .start();
        }

        if (selectedStyle == CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT) {
            slideHighlightBoxTo(wordSlotActive);
        } else if (highlightBoxView.getAlpha() != 0f) {
            highlightBoxView.animate().alpha(0f).setDuration(100).start();
        }
    }

    // Measures the active slot on the next layout pass (its text/width
    // just changed) and animates the shared box View to that position
    // and size — a shared-element-style slide between words.
    private void slideHighlightBoxTo(TextView target) {
        target.post(() -> {
            int widthPx = target.getWidth() + (int) dpToPx(8);
            int heightPx = target.getHeight() + (int) dpToPx(4);
            float targetX = target.getTranslationX();
            float targetY = target.getTranslationY();

            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) highlightBoxView.getLayoutParams();
            boolean firstShow = highlightBoxView.getAlpha() == 0f;
            lp.width = widthPx;
            lp.height = heightPx;
            highlightBoxView.setLayoutParams(lp);

            if (firstShow) {
                highlightBoxView.setTranslationX(targetX);
                highlightBoxView.setTranslationY(targetY);
                highlightBoxView.animate().alpha(1f).setDuration(80).start();
            } else {
                if (boxSlideAnimator != null) {
                    boxSlideAnimator.cancel();
                }
                float startX = highlightBoxView.getTranslationX();
                float startY = highlightBoxView.getTranslationY();
                boxSlideAnimator = ValueAnimator.ofFloat(0f, 1f);
                boxSlideAnimator.setDuration(120);
                boxSlideAnimator.setInterpolator(new DecelerateInterpolator());
                boxSlideAnimator.addUpdateListener(anim -> {
                    float f = (float) anim.getAnimatedValue();
                    highlightBoxView.setTranslationX(startX + (targetX - startX) * f);
                    highlightBoxView.setTranslationY(startY + (targetY - startY) * f);
                });
                boxSlideAnimator.start();
            }
        });
    }

    // Single resync entry point. Called on process resume and on every
    // surface (re)creation — i.e. whenever the app was backgrounded and
    // is coming back. Only re-attaches the polling clock to the current
    // MediaPlayer position; never touches or regenerates `captions`.
    private void resyncCaptionEngineIfNeeded() {
        if (captions != null && !captions.isEmpty()) {
            stopCaptionUpdates();
            lastAnchorIndex = -1; // forces exactly one clean re-render, no flicker
            startCaptionUpdates();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resyncCaptionEngineIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCaptionUpdates();
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

                    // Only touch views when the active word actually
                    // changes. Re-running setText()/spans every tick on
                    // unchanged text is what caused visible flicker and
                    // blocked a clean per-word transition animation.
                    if (anchorIndex != lastAnchorIndex) {
                        lastAnchorIndex = anchorIndex;

                        boolean oneWordPunch = selectedStyle
                                == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH;

                        String beforeWord = (!oneWordPunch && anchorIndex - 1 >= 0)
                                ? captions.get(anchorIndex - 1).word : "";
                        String activeWord = captions.get(anchorIndex).word;
                        String afterWord = (!oneWordPunch && anchorIndex + 1 < captions.size())
                                ? captions.get(anchorIndex + 1).word : "";

                        applySlotStyle(wordSlotBefore, beforeWord, false);
                        applySlotStyle(wordSlotActive, activeWord, true);
                        applySlotStyle(wordSlotAfter, afterWord, false);

                        autoSpaceSlots();
                        playActiveWordTransition();
                    }
                }
                // ~30fps sampling — tight enough for word-boundary snapping
                // without adding meaningful CPU cost over the old 100ms tick.
                captionUpdateHandler.postDelayed(this, 33);
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
                    resyncCaptionEngineIfNeeded();
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
        if (boxSlideAnimator != null) {
            boxSlideAnimator.cancel();
        }

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
