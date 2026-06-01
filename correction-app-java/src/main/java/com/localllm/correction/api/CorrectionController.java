package com.localllm.correction.api;

import com.localllm.correction.core.Modes;
import com.localllm.correction.core.Pipeline;
import com.localllm.correction.core.PipelineResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class CorrectionController {
    private final Pipeline pipeline;

    public CorrectionController(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping("/correct")
    public PipelineResult correct(@Valid @RequestBody CorrectRequest request) {
        return pipeline.correct(request.getText(), request.getMode());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "ok",
            "capabilities", pipeline.capabilities()
        );
    }

    @GetMapping("/modes")
    public Map<String, Object> modes() {
        return Map.of(
            "modes", List.of(Modes.QUICK, Modes.STANDARD, Modes.DEEP),
            "default", Modes.STANDARD
        );
    }
}
