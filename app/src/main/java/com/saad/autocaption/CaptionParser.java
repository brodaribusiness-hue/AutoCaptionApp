package com.saad.autocaption;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CaptionParser {

    private static final String TAG = "CaptionParser";

    // Minimum gap enforced between the end of one word and the start of
    // the next, so two caption events can never be visible at the exact
    // same instant (that was causing garbled/overlapping text on screen).
    private static final float MIN_GAP_SEC = 0.02f;
    private static final float MIN_WORD_DURATION_SEC = 0.05f;

    public static List<Caption> parseVoskResults(List<String> jsonResults) {
        List<Caption> allCaptions = new ArrayList<>();
        if (jsonResults == null) {
            return allCaptions;
        }
        for (String jsonResult : jsonResults) {
            allCaptions.addAll(parseVoskResult(jsonResult));
        }
        sanitizeTimings(allCaptions);
        return allCaptions;
    }

    public static List<Caption> parseVoskResult(String jsonResult) {
        List<Caption> captions = new ArrayList<>();

        if (jsonResult == null || jsonResult.trim().isEmpty()) {
            return captions;
        }

        try {
            JSONObject json = new JSONObject(jsonResult);

            if (json.has("result")) {
                JSONArray resultArray = json.getJSONArray("result");

                for (int i = 0; i < resultArray.length(); i++) {
                    JSONObject item = resultArray.getJSONObject(i);

                    if (item.has("word")) {
                        String word = item.getString("word");
                        float startTime = (float) item.optDouble("start", 0.0);
                        float endTime = (float) item.optDouble("end", 0.0);
                        float confidence = (float) item.optDouble("conf", 1.0);

                        // Fallback safety: endTime must be greater than startTime
                        if (endTime <= startTime) {
                            endTime = startTime + 0.35f;
                        }

                        Caption caption = new Caption(word, startTime, endTime, confidence);
                        captions.add(caption);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse Vosk result chunk: " + jsonResult, e);
        }

        return captions;
    }

    // Vosk can occasionally hand back words whose timestamps overlap by a
    // few milliseconds (especially across streaming-chunk boundaries).
    // When that happens, two words end up "active" at the exact same
    // instant, so both get drawn on top of each other -> garbled text in
    // both the live preview and the exported video. This pass guarantees
    // the word list is sorted and strictly non-overlapping in time.
    private static void sanitizeTimings(List<Caption> captions) {
        if (captions == null || captions.size() < 2) return;

        Collections.sort(captions, new Comparator<Caption>() {
            @Override
            public int compare(Caption a, Caption b) {
                return Float.compare(a.startTime, b.startTime);
            }
        });

        for (int i = 0; i < captions.size() - 1; i++) {
            Caption current = captions.get(i);
            Caption next = captions.get(i + 1);

            if (next.startTime < current.startTime + MIN_WORD_DURATION_SEC) {
                next.startTime = current.startTime + MIN_WORD_DURATION_SEC;
            }

            if (current.endTime > next.startTime - MIN_GAP_SEC) {
                current.endTime = next.startTime - MIN_GAP_SEC;
            }

            if (current.endTime <= current.startTime) {
                current.endTime = current.startTime + MIN_WORD_DURATION_SEC;
            }
        }

        Caption last = captions.get(captions.size() - 1);
        if (last.endTime <= last.startTime) {
            last.endTime = last.startTime + MIN_WORD_DURATION_SEC;
        }
    }

    // GAP-PROOF: 0.35s tolerance buffer stops sudden text disappearing
    public static String getCaptionAtTime(List<Caption> captions, long currentTimeMs) {
        if (captions == null || captions.isEmpty()) {
            return "";
        }

        float currentTimeSec = currentTimeMs / 1000.0f;
        StringBuilder sb = new StringBuilder();

        for (Caption caption : captions) {
            if (currentTimeSec >= caption.startTime && currentTimeSec <= (caption.endTime + 0.35f)) {
                sb.append(caption.word).append(" ");
            }
        }

        return sb.toString().trim();
    }
}
