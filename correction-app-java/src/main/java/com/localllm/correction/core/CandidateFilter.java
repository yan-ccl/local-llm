package com.localllm.correction.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CandidateFilter {
    private CandidateFilter() {
    }

    public static FilterResult filter(List<Edit> edits, String text, Thresholds thresholds, Set<Pair> confusion) {
        List<Edit> kept = new ArrayList<>();
        List<Edit> uncertain = new ArrayList<>();
        List<Edit> dropped = new ArrayList<>();

        int length = Math.max(1, text.length());
        int changed = edits.stream().mapToInt(e -> e.end() - e.start()).sum();
        boolean suspicious = (double) changed / length > thresholds.maxModifyRatio();

        for (Edit edit : edits) {
            if (Sources.RULE.equals(edit.source())) {
                kept.add(edit);
                continue;
            }

            double confidence = edit.confidence();
            if (confusion.contains(new Pair(edit.original(), edit.suggestion()))) {
                confidence = Math.min(0.99, confidence + 0.10);
            }
            if (suspicious) {
                confidence -= 0.10;
            }

            if (confidence >= thresholds.highConfidence()) {
                kept.add(edit);
            } else if (confidence >= thresholds.midConfidence()) {
                uncertain.add(edit);
            } else {
                dropped.add(edit);
            }
        }

        return new FilterResult(kept, uncertain, dropped);
    }
}
