package com.localllm.correction.core;

public final class ErrorTypes {
    public static final String TYPO = "错别字";
    public static final String PUNCT = "标点";
    public static final String WIDTH = "全半角";
    public static final String SPACE = "空格";
    public static final String BLACKLIST = "黑名单";
    public static final String MISSING = "漏字";
    public static final String REDUNDANT = "多字";
    public static final String GRAMMAR = "语病";

    private ErrorTypes() {
    }
}
