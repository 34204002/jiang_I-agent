package com.jiang;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionChunk;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.model.ApiKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 测试 Spring AI DeepSeek 专用 starter 的 reasoning_content 处理能力。
 * <p>
 * 核心问题：OpenAI adapter 的流式响应丢 reasoningContent，
 * DeepSeek 专用 {@code ChatCompletionMessage} 自带 reasoningContent 字段，理论上能正确反序列化。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class DeepSeekStarterTest {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekStarterTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // ==================== 1. 同步调用 ====================

    @Test
    void testDeepSeekApiSyncWithThinking() throws Exception {
        log.info("=== DeepSeek 专用 API 同步 + thinking ===");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "deepseek-v4-flash");
        body.put("messages", List.of(Map.of("role", "user", "content", "1+1等于几？什么情况下不等于2？")));
        body.put("stream", false);
        body.put("thinking", Map.of("type", "enabled"));
        body.put("max_tokens", 800);

        String json = MAPPER.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 用 DeepSeek 专用类型解析
        var completion = MAPPER.readValue(resp.body(), DeepSeekApi.ChatCompletion.class);
        var msg = completion.choices().get(0).message();

        log.info("同步 content: {}", msg.content() != null ? msg.content().substring(0, Math.min(100, msg.content().length())) + "..." : "(空)");
        log.info("同步 reasoningContent: {} chars -> {}",
                msg.reasoningContent() != null ? msg.reasoningContent().length() : 0,
                msg.reasoningContent() != null && !msg.reasoningContent().isEmpty() ? "🟢 有" : "🔴 空");

        if (msg.reasoningContent() == null || msg.reasoningContent().isEmpty()) {
            throw new AssertionError("DeepSeek 同步 reasoningContent 为空（专用 API）");
        }
    }

    // ==================== 2. 流式调用（核心测试） ====================

    @Test
    void testDeepSeekApiStreamWithThinking() throws Exception {
        log.info("=== DeepSeek 专用 API 流式 + thinking ===");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "deepseek-v4-flash");
        body.put("messages", List.of(Map.of("role", "user", "content", "用Java写一个快速排序")));
        body.put("stream", true);
        body.put("thinking", Map.of("type", "enabled"));
        body.put("max_tokens", 800);

        String json = MAPPER.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        int[] rcChunks = {0}, totalChunks = {0};

        httpClient.send(request, HttpResponse.BodyHandlers.ofLines())
                .body()
                .filter(line -> line.startsWith("data: ") && !line.equals("data: [DONE]"))
                .map(line -> line.substring(6))
                .forEach(line -> {
                    try {
                        ChatCompletionChunk chunk = MAPPER.readValue(line, ChatCompletionChunk.class);
                        totalChunks[0]++;
                        var delta = chunk.choices().get(0).delta();

                        // 直接用 DeepSeek 专用类型的 reasoningContent() 方法
                        String rc = delta.reasoningContent();
                        if (rc != null && !rc.isEmpty()) {
                            rcChunks[0]++;
                            reasoning.append(rc);
                        }

                        String c = delta.content();
                        if (c != null && !c.isEmpty()) {
                            content.append(c);
                        }
                    } catch (Exception e) {
                        // 跳过解析失败的行（如 [DONE]）
                    }
                });

        // 等待流结束
        Thread.sleep(3000);

        log.info("流式总chunk: {} | rcChunk: {} | reasoning: {} chars | content: {} chars",
                totalChunks[0], rcChunks[0], reasoning.length(), content.length());

        String verdict;
        if (reasoning.length() > 0) {
            verdict = "🟢 DeepSeek 专用 API 流式 reasoningContent 可用！";
        } else {
            verdict = "🔴 DeepSeek 专用 API 流式 reasoningContent 为空（可能 typing 不支持 thinking，或用 deepseek-reasoner）";
        }
        log.info("结论: {}", verdict);
        log.info("reasoning 前 200 字: {}", reasoning.substring(0, Math.min(200, reasoning.length())));

        if (reasoning.length() == 0) {
            throw new AssertionError(verdict);
        }
    }

    // ==================== 3. 对比：OpenAI adapter 类型能否解析 ====================

    @Test
    void testOpenAiTypeVsDeepSeekType() throws Exception {
        log.info("=== 对比：同一份流式响应，两种类型解析 ===");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "deepseek-v4-flash");
        body.put("messages", List.of(Map.of("role", "user", "content", "Java中volatile关键字的作用")));
        body.put("stream", true);
        body.put("thinking", Map.of("type", "enabled"));
        body.put("max_tokens", 500);

        String json = MAPPER.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        StringBuilder dsReasoning = new StringBuilder();
        StringBuilder oaReasoning = new StringBuilder();
        int[] dsCount = {0}, oaCount = {0};

        httpClient.send(request, HttpResponse.BodyHandlers.ofLines())
                .body()
                .filter(line -> line.startsWith("data: ") && !line.equals("data: [DONE]"))
                .map(line -> line.substring(6))
                .forEach(line -> {
                    try {
                        // 方式 1: DeepSeek 专用类型
                        ChatCompletionChunk dsChunk = MAPPER.readValue(line, ChatCompletionChunk.class);
                        String dsRc = dsChunk.choices().get(0).delta().reasoningContent();
                        if (dsRc != null && !dsRc.isEmpty()) {
                            dsCount[0]++;
                            dsReasoning.append(dsRc);
                        }

                        // 方式 2: 原始 JsonNode 模拟 OpenAI adapter（metadata 方式）
                        var root = MAPPER.readTree(line);
                        var delta = root.path("choices").get(0).path("delta");
                        var rcNode = delta.get("reasoning_content");
                        if (rcNode != null && !rcNode.isNull() && !rcNode.asText().isEmpty()) {
                            oaCount[0]++;
                            oaReasoning.append(rcNode.asText());
                        }
                    } catch (Exception e) { /* skip */ }
                });

        Thread.sleep(3000);

        log.info("DeepSeek 专用类型: {} chunks | {} chars reasoning", dsCount[0], dsReasoning.length());
        log.info("OpenAI raw JSON:  {} chunks | {} chars reasoning", oaCount[0], oaReasoning.length());
        log.info("结论: {}",
                dsReasoning.length() > 0
                        ? "🟢 DeepSeek 专用 ChatCompletionChunk 能正确反序列化 reasoning_content"
                        : "🔴 两种方式都失败");
    }
}
