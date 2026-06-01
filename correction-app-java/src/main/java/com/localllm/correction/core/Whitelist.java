package com.localllm.correction.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Whitelist {
    private final List<String> terms;

    public Whitelist(List<String> terms) {
        Set<String> unique = new LinkedHashSet<>(terms);
        this.terms = unique.stream()
            .filter(term -> term != null && !term.isBlank())
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
    }

    public List<Span> protectedSpans(String text) {
        List<Span> spans = new ArrayList<>();
        for (String term : terms) {
            int start = text.indexOf(term);
            while (start >= 0) {
                spans.add(new Span(start, start + term.length()));
                start = text.indexOf(term, start + 1);
            }
        }
        return spans;
    }
}
