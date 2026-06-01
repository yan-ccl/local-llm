package com.localllm.correction.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleLayer {
    private static final Map<Character, Character> HALF_TO_FULL = Map.of(
        ',', '，',
        '.', '。',
        '?', '？',
        '!', '！',
        ':', '：',
        ';', '；'
    );
    private static final Pattern REPEAT_PUNCT = Pattern.compile("([。，！？、])\\1+");

    private final Map<String, String> blacklist;

    public RuleLayer(Map<String, String> blacklist) {
        this.blacklist = new LinkedHashMap<>(blacklist);
    }

    public List<Edit> apply(String text) {
        List<Edit> edits = new ArrayList<>();
        edits.addAll(fullWidthPunctuation(text));
        edits.addAll(spaceBetweenCjk(text));
        edits.addAll(repeatedPunctuation(text));
        edits.addAll(blacklist(text));
        return edits;
    }

    private List<Edit> fullWidthPunctuation(String text) {
        List<Edit> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            Character full = HALF_TO_FULL.get(ch);
            if (full == null) {
                continue;
            }
            char left = i > 0 ? text.charAt(i - 1) : 0;
            char right = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (isCjk(left) && isCjk(right)) {
                out.add(new Edit(i, i + 1, String.valueOf(ch), String.valueOf(full), Sources.RULE, 0.95, ErrorTypes.WIDTH));
            }
        }
        return out;
    }

    private List<Edit> spaceBetweenCjk(String text) {
        List<Edit> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') {
                continue;
            }
            char left = i > 0 ? text.charAt(i - 1) : 0;
            char right = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (isCjk(left) && isCjk(right)) {
                out.add(new Edit(i, i + 1, " ", "", Sources.RULE, 0.90, ErrorTypes.SPACE));
            }
        }
        return out;
    }

    private List<Edit> repeatedPunctuation(String text) {
        List<Edit> out = new ArrayList<>();
        Matcher matcher = REPEAT_PUNCT.matcher(text);
        while (matcher.find()) {
            out.add(new Edit(
                matcher.start(),
                matcher.end(),
                text.substring(matcher.start(), matcher.end()),
                text.substring(matcher.start(), matcher.start() + 1),
                Sources.RULE,
                0.90,
                ErrorTypes.PUNCT
            ));
        }
        return out;
    }

    private List<Edit> blacklist(String text) {
        List<Edit> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : blacklist.entrySet()) {
            String wrong = entry.getKey();
            String right = entry.getValue();
            if (wrong.isEmpty()) {
                continue;
            }
            int start = text.indexOf(wrong);
            while (start >= 0) {
                out.add(new Edit(start, start + wrong.length(), wrong, right, Sources.RULE, 0.99, ErrorTypes.BLACKLIST));
                start = text.indexOf(wrong, start + wrong.length());
            }
        }
        return out;
    }

    private static boolean isCjk(char ch) {
        return ch >= '\u4e00' && ch <= '\u9fff';
    }
}
