package com.localllm.correction.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CoreTests {
    @Test
    void rulesFixSafeCases() {
        RuleLayer rules = new RuleLayer(Map.of("帐号", "账号"));

        assertThat(Merger.merge("你好,世界", rules.apply("你好,世界"), List.of()).corrected())
            .isEqualTo("你好，世界");
        assertThat(Merger.merge("我的帐号", rules.apply("我的帐号"), List.of()).corrected())
            .isEqualTo("我的账号");
    }

    @Test
    void mergeProtectsWhitelist() {
        Edit edit = new Edit(3, 4, "P", "p", Sources.LLM, 0.9, ErrorTypes.TYPO);
        MergeResult result = Merger.merge("我爱用Python写码", List.of(edit), new Whitelist(List.of("Python")).protectedSpans("我爱用Python写码"));

        assertThat(result.corrected()).isEqualTo("我爱用Python写码");
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).reason()).isEqualTo("whitelist");
    }

    @Test
    void alignHandlesReplacementInsertionAndDeletion() {
        assertThat(Aligner.align("我新情好", "我心情好", Sources.LLM, 0.75))
            .extracting(Edit::suggestion)
            .containsExactly("心");

        assertThat(Aligner.align("我去", "我要去", Sources.LLM, 0.75).get(0).type())
            .isEqualTo(ErrorTypes.MISSING);

        assertThat(Aligner.align("我我去", "我去", Sources.LLM, 0.75).get(0).type())
            .isEqualTo(ErrorTypes.REDUNDANT);
    }

    @Test
    void pipelineWorksRulesOnly() {
        Pipeline pipeline = new Pipeline(
            new RuleLayer(Map.of("帐号", "账号")),
            new Whitelist(List.of()),
            null,
            null,
            Thresholds.defaults(),
            Set.of()
        );

        PipelineResult result = pipeline.correct("我的帐号很好。", Modes.STANDARD);
        assertThat(result.corrected()).isEqualTo("我的账号很好。");
        assertThat(result.sourcesUsed()).containsExactly(Sources.RULE);
    }
}
