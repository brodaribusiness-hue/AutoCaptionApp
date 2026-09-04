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

            // Passing Slot 1, Slot 2, and Slot 3 configurations independently
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
