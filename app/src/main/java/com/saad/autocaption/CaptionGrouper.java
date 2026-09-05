package com.saad.autocaption;

import java.util.ArrayList;
import java.util.List;

public class CaptionGrouper {
    public static final int DEFAULT_GROUP_SIZE = 3;

    public static class Group {
        public final List<Caption> words;
        public final float startTime;
        public final float endTime;

        Group(List<Caption> words) {
            this.words = words;
            this.startTime = words.get(0).startTime;
            this.endTime = words.get(words.size() - 1).endTime;
        }

        public int activeIndexAt(float currentTimeSec) {
            for (int i = 0; i < words.size(); i++) {
                Caption w = words.get(i);
                if (currentTimeSec >= w.startTime && currentTimeSec < w.endTime) {
                    return i;
                }
            }
            return -1;
        }

        public int nearestIndexAt(float currentTimeSec) {
            int idx = activeIndexAt(currentTimeSec);
            if (idx != -1) return idx;
            int nearest = 0;
            for (int i = 0; i < words.size(); i++) {
                if (words.get(i).startTime <= currentTimeSec) {
                    nearest = i;
                }
            }
            return nearest;
        }
    }

    public static List<Group> group(List<Caption> captions) {
        return group(captions, DEFAULT_GROUP_SIZE);
    }

    public static List<Group> group(List<Caption> captions, int groupSize) {
        List<Group> groups = new ArrayList<>();
        if (captions == null || captions.isEmpty()) {
            return groups;
        }
        for (int i = 0; i < captions.size(); i += groupSize) {
            int end = Math.min(i + groupSize, captions.size());
            groups.add(new Group(new ArrayList<>(captions.subList(i, end))));
        }
        return groups;
    }

    // GAP-PROOF INDEX MATCHER: Silence/pauses mein captions ghaib nahi honge
    public static int groupIndexAt(List<Group> groups, float currentTimeSec) {
        if (groups == null || groups.isEmpty()) return -1;

        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            if (currentTimeSec >= g.startTime && currentTimeSec <= g.endTime + 0.35f) {
                return i;
            }
        }

        int nearest = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).startTime <= currentTimeSec) {
                nearest = i;
            }
        }
        return nearest;
    }
}
