package com.localllm.correction.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Pipeline {
    private final RuleLayer ruleLayer;
    private final Whitelist whitelist;
    private final CandidateSource macbert;
    private final CandidateSource llm;
    private final Thresholds thresholds;
    private final Set<Pair> confusion;

    public Pipeline(
        RuleLayer ruleLayer,
        Whitelist whitelist,
        CandidateSource macbert,
        CandidateSource llm,
        Thresholds thresholds,
        Set<Pair> confusion
    ) {
        this.ruleLayer = ruleLayer;
        this.whitelist = whitelist;
        this.macbert = macbert;
        this.llm = llm;
        this.thresholds = thresholds;
        this.confusion = confusion;
    }

    public Map<String, Boolean> capabilities() {
        Map<String, Boolean> caps = new LinkedHashMap<>();
        caps.put("rule", true);
        caps.put("macbert", has(macbert));
        caps.put("llm", has(llm));
        return caps;
    }

    public PipelineResult correct(String text, String requestedMode) {
        long startNanos = System.nanoTime();
        ModeResolution resolution = resolveMode(requestedMode);
        String mode = resolution.mode();
        boolean degraded = resolution.degraded();
        List<String> notes = new ArrayList<>(resolution.notes());
        List<String> sourcesUsed = new ArrayList<>();
        sourcesUsed.add(Sources.RULE);

        List<Edit> ruleEdits = ruleLayer.apply(text);

        boolean macbertAvailable = has(macbert);
        List<Edit> macbertEdits = List.of();
        if (macbertAvailable) {
            try {
                macbertEdits = macbert.propose(text);
                sourcesUsed.add(macbert.name());
            } catch (Exception exc) {
                macbertAvailable = false;
                notes.add("macbert 调用失败，已跳过：" + exc.getMessage());
            }
        }

        FilterResult filterResult = CandidateFilter.filter(macbertEdits, text, thresholds, confusion);
        List<Edit> candidates = new ArrayList<>();
        candidates.addAll(ruleEdits);
        candidates.addAll(filterResult.kept());

        if (shouldRunLlm(mode, filterResult.uncertain().size(), macbertAvailable)) {
            try {
                List<Edit> llmEdits = llm.propose(text);
                sourcesUsed.add(llm.name());
                llmEdits = guardLlm(llmEdits, text, notes);
                candidates.addAll(corroborate(filterResult.uncertain(), llmEdits));
                candidates.addAll(llmEdits);
            } catch (Exception exc) {
                notes.add("LLM 调用失败，已跳过：" + exc.getMessage());
                if (Modes.DEEP.equals(mode)) {
                    degraded = true;
                }
            }
        }

        MergeResult merged = Merger.merge(text, candidates, whitelist.protectedSpans(text));
        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        return new PipelineResult(
            text,
            merged.corrected(),
            mode,
            merged.accepted(),
            merged.rejected(),
            sourcesUsed,
            degraded,
            notes,
            Math.round(elapsedMs * 100.0) / 100.0
        );
    }

    private boolean has(CandidateSource source) {
        return source != null && source.available();
    }

    private ModeResolution resolveMode(String mode) {
        String resolved = mode != null && Modes.ALL.contains(mode) ? mode : Modes.STANDARD;
        boolean degraded = false;
        List<String> notes = new ArrayList<>();
        if (Modes.DEEP.equals(resolved) && !has(llm)) {
            resolved = Modes.STANDARD;
            degraded = true;
            notes.add("LLM 不可用：deep 已降级为 standard（仅 rules）。");
        }
        return new ModeResolution(resolved, degraded, notes);
    }

    private boolean shouldRunLlm(String mode, int uncertainCount, boolean macbertAvailable) {
        if (!has(llm) || Modes.QUICK.equals(mode)) {
            return false;
        }
        if (Modes.DEEP.equals(mode)) {
            return true;
        }
        return uncertainCount > 0 || !macbertAvailable;
    }

    private List<Edit> guardLlm(List<Edit> llmEdits, String text, List<String> notes) {
        int touched = llmEdits.stream()
            .mapToInt(edit -> Math.max(edit.end() - edit.start(), edit.suggestion().length()))
            .sum();
        if ((double) touched / Math.max(1, text.length()) > thresholds.maxModifyRatio()) {
            notes.add("LLM 改动比例过高（" + touched + "/" + text.length() + "），疑似整句改写，已忽略其候选。");
            return List.of();
        }
        return llmEdits;
    }

    private List<Edit> corroborate(List<Edit> uncertain, List<Edit> llmEdits) {
        Set<String> keys = llmEdits.stream()
            .map(edit -> edit.start() + "\u0000" + edit.end() + "\u0000" + edit.suggestion())
            .collect(java.util.stream.Collectors.toSet());
        return uncertain.stream()
            .filter(edit -> keys.contains(edit.start() + "\u0000" + edit.end() + "\u0000" + edit.suggestion()))
            .toList();
    }

    private record ModeResolution(String mode, boolean degraded, List<String> notes) {
    }
}
