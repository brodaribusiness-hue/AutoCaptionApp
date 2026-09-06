package com.saad.autocaption;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CaptionParser {

    private static final String TAG = "CaptionParser";

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
