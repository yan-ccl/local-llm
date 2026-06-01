package com.localllm.correction.api;

import com.localllm.correction.core.Modes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CorrectRequest {
    @NotBlank
    @Size(max = 2000)
    private String text;

    private String mode = Modes.STANDARD;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMode() {
        return mode == null || mode.isBlank() ? Modes.STANDARD : mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
