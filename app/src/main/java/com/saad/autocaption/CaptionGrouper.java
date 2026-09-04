package com.saad.autocaption;
import java.util.ArrayList;
import java.util.List;
/** Groups flat per-word captions into fixed-size blocks (default 3
 * words). This is what keeps the on-screen caption stable: instead of
 * recomputing "before/active/after" from a sliding window centered on
 * whichever word is currently playing (which makes the whole triplet
 * shift every time the active word advances, looking like a
 * typewriter effect), the 3 words in a block are fixed up front and
 * only the *highlight* moves between them as playback progresses.
 * Both the live preview (MainActivity) and the exported .ass file
 * (AssSubtitleBuilder) share this exact grouping so preview and
 * export always match. */
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
        /** Index of the word actively being spoken at currentTimeSec,
         * or -1 if currentTimeSec falls in a gap between words. */
        public int activeIndexAt(float currentTimeSec) {
            for (int i = 0; i < words.size(); i++) {
                Caption w = words.get(i);
                if (currentTimeSec >= w.startTime && currentTimeSec < w.endTime) {
                    return i;
                }
            }
            return -1;
        }
        /** Like activeIndexAt, but never returns -1: falls back to the
         * most recently started word in this group (used for gaps,
         * e.g. between two words' timestamps). */
        public int nearestIndexAt(float currentTimeSec) {
            int idx = activeIndexAt(currentTimeSec);
            if (idx != -1) return idx;
            int nearest = 0;
            for (int i = 0; i < words.size(); i++) {
                if (words.get(i).startTime <= currentTimeSec) nearest = i;
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

    /** Which group is showing at currentTimeSec. Returns -1 only if
     * groups is empty. */
    public static int groupIndexAt(List<Group> groups, float currentTimeSec) {
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            if (currentTimeSec >= g.startTime && currentTimeSec < g.endTime) {
                return i;
            }
        }
        int fallback = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).startTime <= currentTimeSec) {
                fallback = i;
            } else {
                break;
            }
        }
        if (fallback == -1) {
            return groups.isEmpty() ? -1 : 0;
        }
        return fallback;
    }
}
