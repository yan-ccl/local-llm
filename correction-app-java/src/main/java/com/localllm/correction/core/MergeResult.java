package com.localllm.correction.core;

import java.util.List;

public record MergeResult(
    String corrected,
    List<Edit> accepted,
    List<RejectedEdit> rejected
) {
}
