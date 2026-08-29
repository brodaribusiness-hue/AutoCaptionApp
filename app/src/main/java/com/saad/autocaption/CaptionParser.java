package com.saad.autocaption;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CaptionParser {

    public static List<Caption> parseVoskResult(String jsonResult) {
        List<Caption> captions = new ArrayList<>();

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

                        Caption caption = new Caption(
                                word,
                                startTime,
                                endTime,
                                confidence);

                        captions.add(caption);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
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
