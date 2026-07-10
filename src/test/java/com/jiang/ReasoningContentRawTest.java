package com.jiang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 HTTP 测试——验证底层 API 响应中是否存在 reasoning_content。
 * <p>
 * 不依赖 Spring AI，直接用 HttpClient 调 SiliconFlow API，
 * 分别测试 R1（原生推理）和 V3.2（enable_thinking）的行为。
 * <p>
 * 运行: ./mvnw exec:java -Dexec.mainClass="com.jiang.ReasoningContentRawTest" \
 * -Dexec.classpathScope=test
 */
public class ReasoningContentRawTest {

    static final String API_KEY = "sk-rzwpnfjvvgxfrbsmmnfilalfyeqjzwtgzczmmxocuaaqjuuu";
    static final String BASE_URL = "https://api.siliconflow.cn/v1";
    static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        testR1Stream();
        testR1Sync();
        testV32ThinkingStream();
    }

    /**
     * R1 流式——原生推理模型，始终返回 reasoning_content
     */
    static void testR1Stream() throws Exception {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  测试 1: DeepSeek-R1 流式               ║");
        System.out.println("╚══════════════════════════════════════════╝");

        String body = buildBody("deepseek-ai/DeepSeek-R1",
                "1 + 1 = ? 一句话回答。", true, false);

        System.out.println("请求体: " + body);
        System.out.println();

        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        int[] reasoningChunks = {0};
        int[] contentChunks = {0};
        int[] total = {0};

        stream(body, data -> {
            total[0]++;
            try {
                JsonNode delta = MAPPER.readTree(data)
                        .path("choices").get(0).path("delta");

                // 检查 reasoning_content
                if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                    String rc = delta.get("reasoning_content").asText();
                    if (!rc.isEmpty()) {
                        reasoningChunks[0]++;
                        reasoning.append(rc);
                    }
                }

                // 检查 content
                if (delta.has("content") && !delta.get("content").isNull()) {
                    String c = delta.get("content").asText();
                    if (!c.isEmpty()) {
                        contentChunks[0]++;
                        content.append(c);
                    }
                }
            } catch (Exception ignored) {
            }
        });

        System.out.println("总 chunk 数: " + total[0]);
        System.out.println("含 reasoning_content 的 chunk: " + reasoningChunks[0]);
        System.out.println("含 content 的 chunk: " + contentChunks[0]);
        System.out.println("reasoning_content 总长度: " + reasoning.length() + " 字符");
        System.out.println("content 总长度: " + content.length() + " 字符");
        System.out.println();

        if (reasoning.length() > 0) {
            System.out.println("🟢 R1 流式响应中存在 reasoning_content!");
            System.out.println("--- 前 500 字符 ---");
            System.out.println(reasoning.substring(0, Math.min(500, reasoning.length())));
        } else {
            System.out.println("🔴 R1 流式响应中没有 reasoning_content");
        }
        System.out.println();
        System.out.println("--- 回答 ---");
        System.out.println(content);
        System.out.println();
    }

    /**
     * R1 同步
     */
    static void testR1Sync() throws Exception {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  测试 2: DeepSeek-R1 同步               ║");
        System.out.println("╚══════════════════════════════════════════╝");

        String body = buildBody("deepseek-ai/DeepSeek-R1",
                "1 + 1 = ?", false, false);

        String resp = sync(body);
        JsonNode root = MAPPER.readTree(resp);
        JsonNode msg = root.path("choices").get(0).path("message");

        // 检查 message 级别的 reasoning_content
        boolean hasRC = msg.has("reasoning_content") && !msg.get("reasoning_content").isNull();
        System.out.println("message 有 reasoning_content: " + hasRC);
        System.out.println("message keys: " + msg.fieldNames().toString());

        if (hasRC) {
            String rc = msg.get("reasoning_content").asText();
            System.out.println("🟢 R1 同步响应中存在 reasoning_content! (" + rc.length() + " 字符)");
            System.out.println("--- 前 300 字符 ---");
            System.out.println(rc.substring(0, Math.min(300, rc.length())));
        } else {
            System.out.println("🔴 R1 同步响应中没有 reasoning_content");
        }
        System.out.println();
        System.out.println("content: " + msg.path("content").asText());
        System.out.println();
    }

    /**
     * V3.2 + enable_thinking 流式
     */
    static void testV32ThinkingStream() throws Exception {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  测试 3: V3.2 + enable_thinking 流式     ║");
        System.out.println("╚══════════════════════════════════════════╝");

        String body = buildBody("deepseek-ai/DeepSeek-V3.2",
                "递归是什么？一句话解释。", true, true);

        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        int[] reasoningChunks = {0};

        stream(body, data -> {
            try {
                JsonNode delta = MAPPER.readTree(data)
                        .path("choices").get(0).path("delta");
                if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                    String rc = delta.get("reasoning_content").asText();
                    if (!rc.isEmpty()) {
                        reasoningChunks[0]++;
                        reasoning.append(rc);
                    }
                }
                if (delta.has("content") && !delta.get("content").isNull()) {
                    String c = delta.get("content").asText();
                    if (!c.isEmpty()) content.append(c);
                }
            } catch (Exception ignored) {
            }
        });

        System.out.println("含 reasoning_content 的 chunk: " + reasoningChunks[0]);
        System.out.println("reasoning_content 总长度: " + reasoning.length() + " 字符");

        if (reasoning.length() > 0) {
            System.out.println("🟢 V3.2 + enable_thinking 流式响应中存在 reasoning_content!");
        } else {
            System.out.println("🔴 V3.2 + enable_thinking 流式响应中没有 reasoning_content");
        }
        System.out.println();
        System.out.println("--- 回答 ---");
        System.out.println(content);
        System.out.println();
    }

    // ========== 工具方法 ==========

    static String buildBody(String model, String prompt,
                            boolean stream, boolean enableThinking) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", prompt));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);
        if (enableThinking) {
            body.put("enable_thinking", true);
            body.put("thinking_budget", 1024);
        }
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String sync(String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException(resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    static void stream(String json, java.util.function.Consumer<String> onEach)
            throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        System.err.println("HTTP " + resp.statusCode());
                        return;
                    }
                    resp.body()
                            .filter(line -> line.startsWith("data: "))
                            .map(line -> line.substring(6))
                            .takeWhile(data -> !"[DONE]".equals(data))
                            .forEach(onEach);
                })
                .join();
    }
}
