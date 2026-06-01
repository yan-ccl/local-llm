package com.localllm.correction.core;

public record Thresholds(
    double highConfidence,
    double midConfidence,
    double maxModifyRatio
) {
    public static Thresholds defaults() {
        return new Thresholds(0.90, 0.60, 0.40);
    }
}
