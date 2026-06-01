package com.localllm.correction.core;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PipelineResult(
    String original,
    String corrected,
    String mode,
    List<Edit> edits,
    List<RejectedEdit> rejected,
    @JsonProperty("sources_used")
    List<String> sourcesUsed,
    boolean degraded,
    List<String> notes,
    @JsonProperty("elapsed_ms")
    double elapsedMs
) {
}
