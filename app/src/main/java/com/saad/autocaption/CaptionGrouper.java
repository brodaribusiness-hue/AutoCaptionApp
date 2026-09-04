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
