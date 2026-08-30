package com.saad.autocaption;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CaptionParser {

    private static final String TAG = "CaptionParser";

    // FIX: Vosk emits one JSON object per detected utterance/pause, not a
    // single JSON for the whole file. This merges all of them, in order,
    // into one caption list. Word timestamps are already continuous
    // across calls on the same Recognizer instance, so no offset math
    // is needed here — just concatenation.
    public static List<Caption> parseVoskResults(List<String> jsonResults) {
        List<Caption> allCaptions = new ArrayList<>();
        if (jsonResults == null) {
            return allCaptions;
        }
        for (String jsonResult : jsonResults) {
            allCaptions.addAll(parseVoskResult(jsonResult));
        }
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

                        Caption caption = new Caption(word, startTime, endTime, confidence);
                        captions.add(caption);
                    }
                }
            }

        } catch (Exception e) {
            // FIX: was silently swallowed with only printStackTrace(),
            // which is invisible in a release build. Now logged with
            // Log.e so a parse failure is actually visible in Logcat.
            Log.e(TAG, "Failed to parse Vosk result chunk: " + jsonResult, e);
        }

        return captions;
    }

    public static String getCaptionAtTime(
            List<Caption> captions,
            long currentTimeMs) {

        float currentTimeSec = currentTimeMs / 1000.0f;

        StringBuilder sb = new StringBuilder();

        for (Caption caption : captions) {
            if (currentTimeSec >= caption.startTime &&
                    currentTimeSec < caption.endTime) {
                sb.append(caption.word).append(" ");
            }
        }

        return sb.toString().trim();
    }
}
