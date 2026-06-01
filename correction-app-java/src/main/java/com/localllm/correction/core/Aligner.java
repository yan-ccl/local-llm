package com.localllm.correction.core;

import java.util.ArrayList;
import java.util.List;

public final class Aligner {
    private Aligner() {
    }

    public static List<Edit> align(String original, String corrected, String source, double confidence) {
        int n = original.length();
        int m = corrected.length();
        int[][] lcs = new int[n + 1][m + 1];

        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (original.charAt(i) == corrected.charAt(j)) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        List<Edit> edits = new ArrayList<>();
        int i = 0;
        int j = 0;
        int origStart = -1;
        int corrStart = -1;

        while (i < n || j < m) {
            if (i < n && j < m && original.charAt(i) == corrected.charAt(j)) {
                if (origStart >= 0) {
                    addEdit(edits, original, corrected, origStart, i, corrStart, j, source, confidence);
                    origStart = -1;
                    corrStart = -1;
                }
                i++;
                j++;
                continue;
            }

            if (origStart < 0) {
                origStart = i;
                corrStart = j;
            }

            if (j >= m || (i < n && lcs[i + 1][j] >= lcs[i][j + 1])) {
                i++;
            } else {
                j++;
            }
        }

        if (origStart >= 0) {
            addEdit(edits, original, corrected, origStart, i, corrStart, j, source, confidence);
        }
        return edits;
    }

    private static void addEdit(
        List<Edit> edits,
        String original,
        String corrected,
        int origStart,
        int origEnd,
        int corrStart,
        int corrEnd,
        String source,
        double confidence
    ) {
        String before = original.substring(origStart, origEnd);
        String after = corrected.substring(corrStart, corrEnd);
        String type;
        if (origStart == origEnd) {
            type = ErrorTypes.MISSING;
        } else if (corrStart == corrEnd) {
            type = ErrorTypes.REDUNDANT;
        } else if (origEnd - origStart == corrEnd - corrStart) {
            type = ErrorTypes.TYPO;
        } else {
            type = ErrorTypes.GRAMMAR;
        }
        edits.add(new Edit(origStart, origEnd, before, after, source, confidence, type));
    }
}
