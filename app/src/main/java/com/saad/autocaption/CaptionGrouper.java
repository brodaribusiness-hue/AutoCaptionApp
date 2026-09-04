package com.saad.autocaption;

import java.util.ArrayList;
import java.util.List;

/** Groups flat per-word captions into fixed-size blocks (default 3).
 * Keeps a caption block visually stable — only the active word's
 * highlight advances within the block, instead of the whole
 * before/active/after window re-picking words every tick. */
public class CaptionGrouper {

    public static class Group {
        public final List<Caption> words;
        public final float startTime;
        public final float endTime;

        Group(List<Caption> words) {
            this.words = words;
            this.startTime = words.get(0).startTime;
            this.endTime = words.get(words.size() - 1).endTime;
        }

        /** Index of the word active at currentTimeSec, or -1 if in a gap. */
        public int activeIndexAt(float currentTimeSec) {
            for (int i = 0; i < words.size(); i++) {
                Caption w = words.get(i);
                if (currentTimeSec >= w.startTime && currentTimeSec < w.endTime) {
                    return i;
                }
            }
            return -1;
        }
    }

    public static List<Group> group(List<Caption> captions, int groupSize) {
        List<Group> groups = new ArrayList<>();
        if (captions == null || captions.isEmpty()) return groups;
        for (int i = 0; i < captions.size(); i += groupSize) {
            int end = Math.min(i + groupSize, captions.size());
            groups.add(new Group(new ArrayList<>(captions.subList(i, end))));
        }
        return groups;
    }

    public static int groupIndexAt(List<Group> groups, float currentTimeSec) {
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            if (currentTimeSec >= g.startTime && currentTimeSec < g.endTime) return i;
        }
        int fallback = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).startTime <= currentTimeSec) fallback = i; else break;
        }
        return fallback == -1 ? (groups.isEmpty() ? -1 : 0) : fallback;
    }
}
