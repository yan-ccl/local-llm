package com.localllm.correction.core;

import java.util.List;

public record FilterResult(
    List<Edit> kept,
    List<Edit> uncertain,
    List<Edit> dropped
) {
}
