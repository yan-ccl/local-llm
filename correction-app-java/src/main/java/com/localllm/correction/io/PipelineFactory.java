package com.localllm.correction.io;

import com.localllm.correction.core.CandidateSource;
import com.localllm.correction.core.Pipeline;
import com.localllm.correction.core.RuleLayer;
import com.localllm.correction.core.Thresholds;
import com.localllm.correction.core.Whitelist;
import com.localllm.correction.llm.LlmSource;

public final class PipelineFactory {
    private PipelineFactory() {
    }

    public static Pipeline fromEnvironment() {
        CandidateSource llm = envFlag("CSC_ENABLE_LLM", true) ? new LlmSource() : null;

        return new Pipeline(
            new RuleLayer(DataFiles.loadBlacklist()),
            new Whitelist(DataFiles.loadWhitelist()),
            null,
            llm,
            Thresholds.defaults(),
            DataFiles.loadConfusion()
        );
    }

    private static boolean envFlag(String name, boolean defaultValue) {
        String value = System.getenv(name);
        if (value == null) {
            return defaultValue;
        }
        return switch (value.strip().toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> defaultValue;
        };
    }
}
