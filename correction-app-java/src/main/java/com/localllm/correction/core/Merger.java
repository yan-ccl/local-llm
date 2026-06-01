package com.localllm.correction.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Merger {
    private static final int RULE_PRIORITY = 100;
    private static final int CONSENSUS_PRIORITY = 80;
    private static final int DEFAULT_PRIORITY = 50;

    private Merger() {
    }

    public static MergeResult merge(String original, List<Edit> edits, List<Span> protectedSpans) {
        List<RejectedEdit> rejected = new ArrayList<>();
        List<Edit> surviving = new ArrayList<>();

        for (Edit edit : edits) {
            boolean protectedHit = protectedSpans.stream().anyMatch(span -> touches(edit, span));
            if (protectedHit) {
                rejected.add(new RejectedEdit(edit, "whitelist"));
            } else {
                surviving.add(edit);
            }
        }

        surviving = collapseConsensus(surviving);
        surviving.sort(Comparator
            .comparingInt(Merger::priority)
            .thenComparingDouble(Edit::confidence)
            .reversed());

        List<Edit> accepted = new ArrayList<>();
        for (Edit edit : surviving) {
            boolean overlap = accepted.stream().anyMatch(existing -> overlap(edit, existing));
            if (overlap) {
                rejected.add(new RejectedEdit(edit, "overlap"));
            } else {
                accepted.add(edit);
            }
        }

        accepted.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end));
        return new MergeResult(apply(original, accepted), accepted, rejected);
    }

    private static int priority(Edit edit) {
        if (Sources.RULE.equals(edit.source())) {
            return RULE_PRIORITY;
        }
        if (Sources.CONSENSUS.equals(edit.source())) {
            return CONSENSUS_PRIORITY;
        }
        return DEFAULT_PRIORITY;
    }

    private static List<Edit> collapseConsensus(List<Edit> edits) {
        Map<Key, List<Edit>> groups = new LinkedHashMap<>();
        for (Edit edit : edits) {
            groups.computeIfAbsent(new Key(edit.start(), edit.end(), edit.suggestion()), ignored -> new ArrayList<>())
                .add(edit);
        }

        List<Edit> out = new ArrayList<>();
        for (List<Edit> group : groups.values()) {
            Set<String> sources = new LinkedHashSet<>();
            for (Edit edit : group) {
                sources.add(edit.source());
            }
            if (sources.size() > 1 && !sources.contains(Sources.RULE)) {
                Edit best = group.stream()
                    .max(Comparator.comparingDouble(Edit::confidence))
                    .orElseThrow();
                out.add(new Edit(
                    best.start(),
                    best.end(),
                    best.original(),
                    best.suggestion(),
                    Sources.CONSENSUS,
                    Math.min(0.99, best.confidence() + 0.10),
                    best.type()
                ));
            } else {
                out.addAll(group);
            }
        }
        return out;
    }

    private static boolean touches(Edit edit, Span span) {
        if (edit.start() == edit.end()) {
            return span.start() < edit.start() && edit.start() < span.end();
        }
        return edit.start() < span.end() && span.start() < edit.end();
    }

    private static boolean overlap(Edit a, Edit b) {
        if (a.start() == a.end() && b.start() == b.end()) {
            return a.start() == b.start();
        }
        if (a.start() == a.end() || b.start() == b.end()) {
            Edit insertion = a.start() == a.end() ? a : b;
            Edit span = a.start() == a.end() ? b : a;
            return span.start() < insertion.start() && insertion.start() < span.end();
        }
        return a.start() < b.end() && b.start() < a.end();
    }

    private static String apply(String original, List<Edit> edits) {
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (Edit edit : edits) {
            out.append(original, cursor, edit.start());
            out.append(edit.suggestion());
            cursor = edit.end();
        }
        out.append(original.substring(cursor));
        return out.toString();
    }

    private record Key(int start, int end, String suggestion) {
    }
}
