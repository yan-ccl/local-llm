package com.localllm.correction.core;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record Edit(
    int start,
    int end,
    String original,
    String suggestion,
    String source,
    double confidence,
    String type
) {
    public Edit {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid span [" + start + ", " + end + ")");
        }
        original = original == null ? "" : original;
        suggestion = suggestion == null ? "" : suggestion;
        type = type == null ? "" : type;
    }

    @JsonIgnore
    public boolean isInsertion() {
        return start == end;
    }

    @JsonIgnore
    public boolean isDeletion() {
        return end > start && suggestion.isEmpty();
    }
}
