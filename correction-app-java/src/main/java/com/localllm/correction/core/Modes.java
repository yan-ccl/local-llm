package com.localllm.correction.core;

import java.util.Set;

public final class Modes {
    public static final String QUICK = "quick";
    public static final String STANDARD = "standard";
    public static final String DEEP = "deep";
    public static final Set<String> ALL = Set.of(QUICK, STANDARD, DEEP);

    private Modes() {
    }
}
