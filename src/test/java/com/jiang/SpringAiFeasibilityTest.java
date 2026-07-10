package com.jiang;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionChunk;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全面测试：Spring AI DeepSeek 专用 API 能否替代自研 HttpClient 框架。
 * <p>
 * 测试矩阵：
 * <ol>
 *   <li>同步 + thinking → reasoningContent 是否可用</li>
 *   <li>流式 + thinking → reasoningContent 是否可用</li>
 *   <li>同步 + tools → tool_calls 是否正常</li>
 *   <li>流式 + tools → 流式 tool_calls 是否正常</li>
 *   <li>thinking + tools → 能否同时工作</li>
 *   <li>reasoning_content 回传 → 工具调用后能否回传 reasoning_content</li>
 *   <li>DeepSeekApi 直接使用 → 能否完全替代 DeepSeekStreamService</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class SpringAiFeasibilityTest {

    private static final Logger log = LoggerFactory.getLogger(SpringAiFeasibilityTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30)).build();
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    // ==================== 1. 同步 + thinking ====================
    @Value("${spring.ai.openai.chat.model:deepseek-v4-flash}")
    private String model;

    // ==================== 2. 流式 + thinking ====================

    @Test
    void test01_syncWithThinking() throws Exception {
        log.info("=== 1. 同步 + thinking ===");

        var req = rawRequest(false,
                Map.of("role", "user", "content", "1+1在什么情况下不等于2？一句话回答"),
                Map.of("thinking", Map.of("type", "enabled")));

        String respBody = syncHttp(req);
        var completion = MAPPER.readValue(respBody, ChatCompletion.class);
        var msg = completion.choices().get(0).message();

        log.info("content: {}", shorten(msg.content()));
        log.info("reasoningContent: {} chars → {}",
                msg.reasoningContent() != null ? msg.reasoningContent().length() : 0,
                hasRC(msg) ? "🟢 可用" : "🔴 空");
        assert hasRC(msg) : "同步 thinking 失败";
    }

    // ==================== 3. 同步 + tools（无 thinking） ====================

    @Test
    void test02_streamWithThinking() throws Exception {
        log.info("=== 2. 流式 + thinking ===");

        var req = rawRequest(true,
                Map.of("role", "user", "content", "用Java写一个冒泡排序"),
                Map.of("thinking", Map.of("type", "enabled")));

        var result = streamCollect(req);
        log.info("content: {} chars | reasoningContent: {} chunks / {} chars",
                result.content.length(), result.rcChunks, result.reasoning.length());
        log.info("→ {}", result.reasoning.length() > 0 ? "🟢 流式 thinking 可用" : "🔴 空");
        assert result.reasoning.length() > 0 : "流式 thinking 失败";
    }

    // ==================== 4. 流式 + tools ====================

    @Test
    void test03_syncWithTools() throws Exception {
        log.info("=== 3. 同步 + tools（无 thinking） ===");

        var req = rawRequest(false,
                Map.of("role", "user", "content", "现在北京时间是几点？"),
                Map.of("tools", List.of(mapOf("type", "function", "function", mapOf(
                        "name", "get_current_time",
                        "description", "获取当前北京时间",
                        "parameters", mapOf("type", "object", "properties", Map.of()))))));

        String json = MAPPER.writeValueAsString(req);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        String respBody = client.send(httpReq, HttpResponse.BodyHandlers.ofString()).body();
        var completion = MAPPER.readValue(respBody, ChatCompletion.class);
        var msg = completion.choices().get(0).message();

        log.info("finishReason: {}", completion.choices().get(0).finishReason());
        log.info("content: {}", shorten(msg.content()));
        log.info("has tool_calls: {} → {}", msg.toolCalls() != null && !msg.toolCalls().isEmpty(),
                msg.toolCalls() != null && !msg.toolCalls().isEmpty() ? "🟢 工具调用正常" : "⚠️ 未触发工具（可能是时区问题，模型选择直接回答）");

        // 不强制 assert——模型可能直接回答而不调工具
    }

    // ==================== 5. thinking + tools 组合（最关键） ====================

    @Test
    void test04_streamWithTools() throws Exception {
        log.info("=== 4. 流式 + tools ===");

        var req = rawRequest(true,
                Map.of("role", "user", "content", "帮我查一下现在几点"),
                Map.of("tools", List.of(mapOf("type", "function", "function", mapOf(
                        "name", "get_current_time",
                        "description", "获取当前北京时间",
                        "parameters", mapOf("type", "object", "properties", Map.of()))))));

        String json = MAPPER.writeValueAsString(req);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        StringBuilder tcName = new StringBuilder();
        StringBuilder tcArgs = new StringBuilder();
        StringBuilder content = new StringBuilder();

        client.send(httpReq, HttpResponse.BodyHandlers.ofLines())
                .body()
                .filter(line -> line.startsWith("data: ") && !line.equals("data: [DONE]"))
                .map(line -> line.substring(6))
                .forEach(line -> {
                    try {
                        ChatCompletionChunk chunk = MAPPER.readValue(line, ChatCompletionChunk.class);
                        var delta = chunk.choices().get(0).delta();

                        if (delta.toolCalls() != null) {
                            for (var tc : delta.toolCalls()) {
                                if (tc.function().name() != null && !tc.function().name().isEmpty())
                                    tcName.append(tc.function().name());
                                if (tc.function().arguments() != null) tcArgs.append(tc.function().arguments());
                            }
                        }
                        if (delta.content() != null && !delta.content().isEmpty()) {
                            content.append(delta.content());
                        }
                    } catch (Exception e) { /* skip */ }
                });

        log.info("toolName: {} | args: {} | content: {} chars",
                tcName, shorten(tcArgs.toString()), content.length());
        log.info("→ {}", tcName.length() > 0 ? "🟢 流式 tool_calls 正常" : "⚠️ 模型未调用工具");
    }

    // ==================== 6. reasoning_content 回传验证 ====================

    @Test
    void test05_thinkingWithTools() throws Exception {
        log.info("=== 5. thinking + tools 组合（核心场景） ===");

        var req = rawRequest(false,
                Map.of("role", "user", "content", "帮我查一下现在北京时间几点，查询完后告诉我当前时间适合做什么"),
                Map.of(
                        "thinking", Map.of("type", "enabled"),
                        "tools", List.of(mapOf("type", "function", "function", mapOf(
                                "name", "get_current_time",
                                "description", "获取当前北京时间",
                                "parameters", mapOf("type", "object", "properties", Map.of()))))));

        String json = MAPPER.writeValueAsString(req);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        String respBody = client.send(httpReq, HttpResponse.BodyHandlers.ofString()).body();

        var completion = MAPPER.readValue(respBody, ChatCompletion.class);
        var msg = completion.choices().get(0).message();

        log.info("reasoningContent: {} chars", msg.reasoningContent() != null ? msg.reasoningContent().length() : 0);
        log.info("has tool_calls: {}", msg.toolCalls() != null && !msg.toolCalls().isEmpty());
        log.info("finishReason: {}", completion.choices().get(0).finishReason());

        boolean hasThinking = hasRC(msg);
        boolean hasTools = msg.toolCalls() != null && !msg.toolCalls().isEmpty();

        if (hasThinking && hasTools) {
            log.info("→ 🟢 thinking + tools 同时工作！");
        } else if (hasThinking && !hasTools) {
            log.info("→ 🟡 thinking 可用但模型未调工具（可能是问题简单不需要工具）");
        } else if (!hasThinking) {
            log.info("→ 🔴 thinking 为空（可能 thinking+tool 不兼容）");
        }
    }

    // ==================== 7. 多轮工具调用 ====================

    @Test
    void test06_reasoningContentPassback() throws Exception {
        log.info("=== 6. reasoning_content 回传验证 ===");

        // 第一轮：获取 thinking + tool_call
        var round1 = rawRequest(false,
                Map.of("role", "user", "content", "现在几点？然后帮我算下距离明天早上8点还有多少小时"),
                Map.of(
                        "thinking", Map.of("type", "enabled"),
                        "tools", List.of(mapOf("type", "function", "function", mapOf(
                                "name", "get_current_time",
                                "description", "获取当前北京时间",
                                "parameters", mapOf("type", "object", "properties", Map.of()))))));

        String r1Json = MAPPER.writeValueAsString(round1);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        HttpResponse<String> r1Resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(r1Json, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());

        var r1Completion = MAPPER.readValue(r1Resp.body(), ChatCompletion.class);
        var r1Msg = r1Completion.choices().get(0).message();
        String r1Reasoning = r1Msg.reasoningContent();

        log.info("第一轮: reasoning={} chars, toolCalls={}",
                r1Reasoning != null ? r1Reasoning.length() : 0,
                r1Msg.toolCalls() != null ? r1Msg.toolCalls().size() : 0);

        if (r1Msg.toolCalls() == null || r1Msg.toolCalls().isEmpty()) {
            log.info("→ ⚠️ 模型未调工具，跳过回传测试");
            return;
        }

        // 构建第二轮消息：必须包含 assistant tool_call 消息 + reasoning_content
        List<Map<String, Object>> round2Messages = new ArrayList<>();
        round2Messages.add(Map.of("role", "user", "content", "现在几点？然后帮我算下距离明天早上8点还有多少小时"));

        // Assistant 消息（含 tool_calls + reasoning_content）
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", r1Msg.content() != null ? r1Msg.content() : "");
        if (r1Reasoning != null && !r1Reasoning.isEmpty()) {
            assistantMsg.put("reasoning_content", r1Reasoning);
        }
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        int idx = 0;
        for (var tc : r1Msg.toolCalls()) {
            toolCalls.add(Map.of(
                    "id", "call_test_" + (idx++),
                    "type", "function",
                    "function", Map.of("name", tc.function().name(), "arguments", tc.function().arguments())));
        }
        assistantMsg.put("tool_calls", toolCalls);
        round2Messages.add(assistantMsg);

        // Tool 结果
        round2Messages.add(Map.of("role", "tool",
                "tool_call_id", "call_test_0",
                "content", "当前北京时间: 2026-06-26 12:00:00（模拟）"));

        var round2 = rawRequestFromMessages(round2Messages, false,
                Map.of("thinking", Map.of("type", "enabled")));

        String r2Json = MAPPER.writeValueAsString(round2);
        HttpResponse<String> r2Resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(r2Json, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());

        // 检查是否 200（400 = reasoning_content missing）
        log.info("第二轮 HTTP status: {}", r2Resp.statusCode());
        if (r2Resp.statusCode() == 200) {
            var r2Completion = MAPPER.readValue(r2Resp.body(), ChatCompletion.class);
            String r2Content = r2Completion.choices().get(0).message().content();
            log.info("第二轮 content: {}", shorten(r2Content));
            log.info("→ 🟢 reasoning_content 回传成功！第二轮正常响应");
        } else {
            log.info("→ 🔴 回传失败 HTTP {}: {}", r2Resp.statusCode(), shorten(r2Resp.body()));
        }
        assert r2Resp.statusCode() == 200 : "reasoning_content 回传失败";
    }

    // ==================== 辅助方法 ====================

    @Test
    void test07_multiRoundToolCall() throws Exception {
        log.info("=== 7. 多轮工具调用 ===");

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "先告诉我现在几点，然后根据时间推荐一个适合的活动"));

        int maxRounds = 3;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        ObjectMapper mapper = new ObjectMapper();

        for (int round = 0; round < maxRounds; round++) {
            var req = rawRequestFromMessages(messages, false,
                    Map.of("tools", List.of(mapOf("type", "function", "function", mapOf(
                            "name", "get_current_time",
                            "description", "获取当前北京时间",
                            "parameters", mapOf("type", "object", "properties", Map.of()))))));

            String json = MAPPER.writeValueAsString(req);
            HttpResponse<String> resp = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofString());

            var completion = MAPPER.readValue(resp.body(), ChatCompletion.class);
            var msg = completion.choices().get(0).message();
            String finishReason = completion.choices().get(0).finishReason() != null
                    ? completion.choices().get(0).finishReason().name() : "null";

            log.info("轮次 {}: finish={}, content={}, toolCalls={}",
                    round + 1, finishReason,
                    msg.content() != null ? shorten(msg.content()) : "(空)",
                    msg.toolCalls() != null ? msg.toolCalls().size() : 0);

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                // 构建 assistant 消息
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                if (msg.content() != null) assistantMsg.put("content", msg.content());
                if (hasRC(msg)) assistantMsg.put("reasoning_content", msg.reasoningContent());
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (int i = 0; i < msg.toolCalls().size(); i++) {
                    var tc = msg.toolCalls().get(i);
                    tcs.add(Map.of("id", "call_r" + round + "_" + i, "type", "function",
                            "function", Map.of("name", tc.function().name(), "arguments", tc.function().arguments())));
                }
                assistantMsg.put("tool_calls", tcs);
                messages.add(assistantMsg);

                // 添加 tool 结果
                for (int i = 0; i < msg.toolCalls().size(); i++) {
                    messages.add(Map.of("role", "tool",
                            "tool_call_id", "call_r" + round + "_" + i,
                            "content", "当前时间: 2026-06-26 15:30:00 北京时间（模拟）"));
                }
            } else {
                log.info("→ 🟢 第{}轮完成，模型给出最终回答", round + 1);
                break;
            }
        }
    }

    /**
     * 同步 HTTP 请求
     */
    private String syncHttp(Map<String, Object> body) throws Exception {
        String json = MAPPER.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /**
     * 构建原始请求 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> rawRequest(boolean stream,
                                           Map<String, Object> userMsg,
                                           Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(userMsg));
        body.put("stream", stream);
        body.putAll(extra);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rawRequestFromMessages(List<Map<String, Object>> messages,
                                                       boolean stream,
                                                       Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);
        body.putAll(extra);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private boolean hasRC(ChatCompletionMessage msg) {
        return msg.reasoningContent() != null && !msg.reasoningContent().isEmpty();
    }

    private String shorten(String s) {
        if (s == null) return "(null)";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private StreamResult streamCollect(Map<String, Object> req) throws Exception {
        String json = MAPPER.writeValueAsString(req);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        int[] rcChunks = {0}, total = {0};

        client.send(httpReq, HttpResponse.BodyHandlers.ofLines())
                .body()
                .filter(line -> line.startsWith("data: ") && !line.equals("data: [DONE]"))
                .map(line -> line.substring(6))
                .forEach(line -> {
                    try {
                        ChatCompletionChunk chunk = MAPPER.readValue(line, ChatCompletionChunk.class);
                        total[0]++;
                        var delta = chunk.choices().get(0).delta();
                        String rc = delta.reasoningContent();
                        if (rc != null && !rc.isEmpty()) {
                            rcChunks[0]++;
                            reasoning.append(rc);
                        }
                        String c = delta.content();
                        if (c != null && !c.isEmpty()) content.append(c);
                    } catch (Exception e) { /* skip */ }
                });

        return new StreamResult(content, reasoning, rcChunks[0], total[0]);
    }

    record StreamResult(StringBuilder content, StringBuilder reasoning, int rcChunks, int totalChunks) {
    }
}
