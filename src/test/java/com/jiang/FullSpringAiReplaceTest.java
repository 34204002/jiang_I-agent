package com.jiang;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 全面测试 Spring AI DeepSeek 专用 starter 能否替代自研框架。
 *
 * 三个核心问题：
 * 1. stream() metadata 里有没有 reasoningContent？（之前 OpenAI adapter 没有）
 * 2. DeepSeekApi 能否完全替代 DeepSeekStreamService？
 * 3. 工具调用 + reasoning_content 回传是否正常？
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class FullSpringAiReplaceTest {

    private static final Logger log = LoggerFactory.getLogger(FullSpringAiReplaceTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    // (test01 removed: DeepSeekChatModel requires ToolCallingManager, not needed — we use DeepSeekApi directly)

    // ==================== 1. ⭐ DeepSeekApi 流式 — 替代 DeepSeekStreamService ====================

    @Test
    void test01_deepSeekApi_stream() throws Exception {
        log.info("=== 1. DeepSeekApi.chatCompletionStream() 替代 DeepSeekStreamService ===");

        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(apiKey).baseUrl(baseUrl).build();

        var messages = List.of(new ChatCompletionMessage("用Java写一个二分查找",
                ChatCompletionMessage.Role.USER, null, null, null));

        var req = new ChatCompletionRequest(messages, "deepseek-v4-flash", 0.7, 800,
                null, null, null, true, null, null, null, null, null, null);

        StringBuilder rc = new StringBuilder();
        StringBuilder content = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        int[] rcCount = {0};

        api.chatCompletionStream(req)
                .doOnNext(chunk -> {
                    var delta = chunk.choices().get(0).delta();
                    if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
                        rcCount[0]++;
                        rc.append(delta.reasoningContent());
                    }
                    if (delta.content() != null && !delta.content().isEmpty()) {
                        content.append(delta.content());
                    }
                })
                .doOnComplete(() -> {
                    log.info("reasoning: {} chunks / {} chars | content: {} chars",
                            rcCount[0], rc.length(), content.length());
                    latch.countDown();
                })
                .doOnError(e -> { log.error("失败: {}", e.getMessage()); latch.countDown(); })
                .subscribe();

        latch.await(120, TimeUnit.SECONDS);

        log.info("content 前100字: {}", content.substring(0, Math.min(100, content.length())));
        log.info("reasoning 前100字: {}", rc.length() > 0 ? rc.substring(0, Math.min(100, rc.length())) : "(空)");
        String verdict = rc.length() > 0
                ? "🟢 DeepSeekApi.chatCompletionStream() 完美替代 DeepSeekStreamService"
                : "🔴 流式没有 reasoning_content";
        log.info("→ {}", verdict);
        assert rc.length() > 0 : verdict;
    }

    // ==================== 3. ⭐ DeepSeekApi 同步 ====================

    @Test
    void test02_deepSeekApi_sync() throws Exception {
        log.info("=== 2. DeepSeekApi.chatCompletionEntity() 同步 ===");

        DeepSeekApi api = DeepSeekApi.builder()
                .apiKey(apiKey).baseUrl(baseUrl).build();

        var messages = List.of(new ChatCompletionMessage("HashMap底层原理，一句话",
                ChatCompletionMessage.Role.USER, null, null, null));

        var req = new ChatCompletionRequest(messages, "deepseek-v4-flash", 0.7, 500,
                null, null, null, false, null, null, null, null, null, null);

        var resp = api.chatCompletionEntity(req).getBody();
        var msg = resp.choices().get(0).message();
        int rcLen = msg.reasoningContent() != null ? msg.reasoningContent().length() : 0;

        log.info("content: {}", shorten(msg.content()));
        log.info("reasoningContent: {} chars", rcLen);
        log.info("→ {}", rcLen > 0 ? "🟢 同步 reasoning_content 正常" : "🔴 空");
    }

    // ==================== 4. ⭐ tools + reasoning_content 同时工作 ====================

    @Test
    void test03_toolsWithReasoning_sync() throws Exception {
        log.info("=== 3. tools + reasoning 同时工作（手动 Map 请求 + DeepSeek 类型解析）===");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "deepseek-v4-flash");
        body.put("messages", List.of(Map.of("role", "user",
                "content", "帮我查当前北京时间，然后推荐一个适合的活动")));
        body.put("stream", false);
        body.put("tools", List.of(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_current_time",
                        "description", "获取当前北京时间",
                        "parameters", Map.of("type", "object", "properties", Map.of())))));

        String json = MAPPER.writeValueAsString(body);
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        var resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());

        var completion = MAPPER.readValue(resp.body(), ChatCompletion.class);
        var msg = completion.choices().get(0).message();
        boolean hasTC = msg.toolCalls() != null && !msg.toolCalls().isEmpty();
        int rcLen = msg.reasoningContent() != null ? msg.reasoningContent().length() : 0;

        log.info("reasoning: {} chars | tool_calls: {}", rcLen, hasTC);
        String verdict = hasTC ? "🟢 tools + reasoning 同时工作" : "🔴 未触发工具";
        log.info("→ {}", verdict);

        // 如果有工具调用 + reasoning，手动做回传
        if (hasTC && rcLen > 0) {
            log.info("→ 第一轮有 reasoning_content，可以做回传验证");

            // 构建第二轮
            List<Map<String, Object>> r2Msgs = new ArrayList<>();
            r2Msgs.add(Map.of("role", "user", "content", body.get("messages") instanceof List<?> l
                    ? ((Map<?,?>) l.get(0)).get("content") : ""));

            Map<String, Object> asst = new LinkedHashMap<>();
            asst.put("role", "assistant");
            if (msg.content() != null) asst.put("content", msg.content());
            asst.put("reasoning_content", msg.reasoningContent());
            List<Map<String, Object>> tcs = new ArrayList<>();
            for (int i = 0; i < msg.toolCalls().size(); i++) {
                var tc = msg.toolCalls().get(i);
                tcs.add(Map.of("id", "call_" + i, "type", "function",
                        "function", Map.of("name", tc.function().name(),
                                "arguments", tc.function().arguments())));
            }
            asst.put("tool_calls", tcs);
            r2Msgs.add(asst);
            r2Msgs.add(Map.of("role", "tool", "tool_call_id", "call_0",
                    "content", "当前北京时间: 2026-06-26 16:00:00（模拟）"));

            Map<String, Object> r2Body = new LinkedHashMap<>();
            r2Body.put("model", "deepseek-v4-flash");
            r2Body.put("messages", r2Msgs);
            r2Body.put("stream", false);

            String r2Json = MAPPER.writeValueAsString(r2Body);
            var r2Resp = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(r2Json, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofString());

            log.info("第二轮 HTTP {}: {}", r2Resp.statusCode(),
                    r2Resp.statusCode() == 200 ? "🟢 reasoning_content 回传成功" : "🔴 回传失败");
        }
    }

    // ==================== 辅助 ====================

    private String shorten(String s) {
        if (s == null) return "(null)";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
