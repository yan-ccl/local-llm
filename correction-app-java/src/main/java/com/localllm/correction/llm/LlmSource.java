package com.localllm.correction.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localllm.correction.core.Aligner;
import com.localllm.correction.core.CandidateSource;
import com.localllm.correction.core.Edit;
import com.localllm.correction.core.Sources;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class LlmSource implements CandidateSource {
    private static final String PROMPT = """
        你是严谨的中文校对助手。请改正下面句子中的错别字、用词错误、语病、标点、漏字和多字。要求：
        1. 只做最小必要纠错，没有把握就保持原样；
        2. 绝不改写句子风格，绝不替换同义词，绝不扩写或缩写原词组；
        3. 不要解释，不要增删与纠错无关的内容；
        4. 严格输出 JSON：{"corrected": "改正后的完整句子"}。

        句子：%s""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final String model;
    private final String baseUrl;
    private final double confidence;
    private final Duration readTimeout;
    private final int maxTokens;
    private final Duration probeTtl;

    private boolean ok;
    private long lastProbeNanos = Long.MIN_VALUE;

    public LlmSource() {
        this(
            envOrDefault("LLM_MODEL", "qwen2.5:1.5b"),
            defaultBaseUrl(),
            0.75,
            Duration.ofMillis(1500),
            Duration.ofSeconds(30),
            512,
            Duration.ofSeconds(10)
        );
    }

    public LlmSource(
        String model,
        String baseUrl,
        double confidence,
        Duration connectTimeout,
        Duration readTimeout,
        int maxTokens,
        Duration probeTtl
    ) {
        this.model = model;
        this.baseUrl = stripSuffix(baseUrl).replaceAll("/+$", "");
        this.confidence = confidence;
        this.readTimeout = readTimeout;
        this.maxTokens = maxTokens;
        this.probeTtl = probeTtl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build();
    }

    @Override
    public String name() {
        return Sources.LLM;
    }

    @Override
    public boolean available() {
        if (ok) {
            return true;
        }
        long now = System.nanoTime();
        if (lastProbeNanos != Long.MIN_VALUE && now - lastProbeNanos < probeTtl.toNanos()) {
            return false;
        }
        lastProbeNanos = now;
        ok = probe();
        return ok;
    }

    @Override
    public List<Edit> propose(String text) throws Exception {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String corrected = generate(text);
        if (corrected.isEmpty() || corrected.equals(text)) {
            return List.of();
        }
        return Aligner.align(text, corrected, Sources.LLM, confidence);
    }

    private boolean probe() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tags"))
                .timeout(Duration.ofMillis(1500))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String generate(String text) throws Exception {
        Map<String, Object> payload = Map.of(
            "model", model,
            "prompt", PROMPT.formatted(text),
            "stream", false,
            "format", "json",
            "options", Map.of(
                "temperature", 0,
                "num_predict", maxTokens
            )
        );
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/generate"))
            .timeout(readTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Ollama returned HTTP " + response.statusCode());
        }
        JsonNode outer = objectMapper.readTree(response.body());
        return parseResponse(outer.path("response").asText(""));
    }

    public String parseResponse(String response) {
        String text = response == null ? "" : response.strip();
        if (text.isEmpty()) {
            return "";
        }

        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.isObject()) {
                JsonNode corrected = node.has("corrected") ? node.get("corrected") : node.get("text");
                if (corrected != null && corrected.isTextual()) {
                    return corrected.asText().strip();
                }
            }
        } catch (Exception ignored) {
            // Fall back to permissive parsing below.
        }

        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            if (firstLine >= 0) {
                text = text.substring(firstLine + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.strip();
        }

        for (String label : List.of("修改后：", "修改后:", "纠正后：", "纠正后:", "Corrected:")) {
            if (text.startsWith(label)) {
                text = text.substring(label.length()).strip();
                break;
            }
        }

        int newline = text.indexOf('\n');
        return newline >= 0 ? text.substring(0, newline).strip() : text.strip();
    }

    private static String defaultBaseUrl() {
        String value = firstNonBlank(
            System.getenv("OLLAMA_BASE_URL"),
            System.getenv("OLLAMA_URL"),
            System.getenv("OLLAMA_HOST")
        );
        return value == null ? "http://localhost:11434" : value;
    }

    private static String stripSuffix(String url) {
        String out = url == null || url.isBlank() ? "http://localhost:11434" : url.strip();
        for (String suffix : List.of("/api/generate", "/api/chat")) {
            if (out.endsWith(suffix)) {
                out = out.substring(0, out.length() - suffix.length());
            }
        }
        return out;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
