package com.localllm.correction.io;

import com.localllm.correction.core.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DataFiles {
    private DataFiles() {
    }

    public static Map<String, String> loadBlacklist() {
        Map<String, String> pairs = new LinkedHashMap<>();
        for (String line : readLines("blacklist.txt")) {
            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2) {
                pairs.put(parts[0], parts[1]);
            }
        }
        return pairs;
    }

    public static Set<Pair> loadConfusion() {
        Set<Pair> pairs = new LinkedHashSet<>();
        for (String line : readLines("confusion.txt")) {
            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2) {
                pairs.add(new Pair(parts[0], parts[1]));
            }
        }
        return pairs;
    }

    public static List<String> loadWhitelist() {
        return readLines("whitelist.txt");
    }

    private static List<String> readLines(String filename) {
        String dataDir = System.getenv("DATA_DIR");
        if (dataDir != null && !dataDir.isBlank()) {
            Path path = Path.of(dataDir, filename);
            if (Files.exists(path)) {
                try {
                    return clean(Files.readAllLines(path, StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    return List.of();
                }
            }
        }

        String resource = "data/" + filename;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = loader.getResourceAsStream(resource)) {
            if (input == null) {
                return List.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return clean(reader.lines().toList());
            }
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static List<String> clean(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (!line.isEmpty() && !line.startsWith("#")) {
                out.add(line);
            }
        }
        return out;
    }
}
