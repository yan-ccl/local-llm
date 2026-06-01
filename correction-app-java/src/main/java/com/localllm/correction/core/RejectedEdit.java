package com.localllm.correction.core;

public record RejectedEdit(Edit edit, String reason) {
}
