package com.localllm.correction.core;

import java.util.List;

public interface CandidateSource {
    String name();

    boolean available();

    List<Edit> propose(String text) throws Exception;
}
