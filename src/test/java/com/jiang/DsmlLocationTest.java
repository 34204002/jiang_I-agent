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
import java.util.Map;

/**
 * 验证 DSML / tool_call 在哪：
 * A) delta.reasoning_content（思考过程，最终会有正经 tool_call）
 * B) delta.content（纯正文，不标准的 DSML 标签）
 *
 * 用 V3.2 + enable_thinking + tools 数组，逐 chunk 打印两个字段。
 */
public class DsmlLocationTest {

    static final String API_KEY = "sk-rzwpnfjvvgxfrbsmmnfilalfyeqjzwtgzczmmxocuaaqjuuu";
    static final String BASE_URL = "https://api.siliconflow.cn/v1";
    static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        test("thinking=true + tools", "deepseek-ai/DeepSeek-V3.2", true);
        System.out.println("\n\n");
        test("thinking=false + tools", "deepseek-ai/DeepSeek-V3.2", false);
    }

    static void test(String label, String model, boolean enableThinking) throws Exception {
        var tools = java.util.List.of(java.util.Map.of(
            "type", "function",
            "function", java.util.Map.of(
                "name", "get_current_time",
                "description", "获取当前时间",
                "parameters", java.util.Map.of("type", "object", "properties", java.util.Map.of())
            )
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", java.util.List.of(
            java.util.Map.of("role", "user", "content", "现在几点？")
        ));
        body.put("stream", true);
        if (enableThinking) {
            body.put("enable_thinking", true);
            body.put("thinking_budget", 1024);
        }
        body.put("tools", tools);

        String json = MAPPER.writeValueAsString(body);
        System.out.println("=== " + label + " ===");
        System.out.println();

        int[] chunkNum = {0};
        int[] reasoningChunks = {0}, contentChunks = {0}, tcChunks = {0};
        StringBuilder allReasoning = new StringBuilder();
        StringBuilder allContent = new StringBuilder();
        StringBuilder allToolCalls = new StringBuilder();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(resp -> {
                    resp.body()
                        .filter(line -> line.startsWith("data: "))
                        .map(line -> line.substring(6))
                        .takeWhile(data -> !"[DONE]".equals(data))
                        .forEach(data -> {
                            chunkNum[0]++;
                            try {
                                JsonNode root = MAPPER.readTree(data);
                                JsonNode delta = root.path("choices").get(0).path("delta");

                                boolean hasReasoning = delta.has("reasoning_content")
                                        && !delta.get("reasoning_content").isNull()
                                        && !delta.get("reasoning_content").asText().isEmpty();
                                boolean hasContent = delta.has("content")
                                        && !delta.get("content").isNull()
                                        && !delta.get("content").asText().isEmpty();
                                boolean hasToolCalls = delta.has("tool_calls")
                                        && !delta.get("tool_calls").isNull();

                                if (hasReasoning) {
                                    reasoningChunks[0]++;
                                    String rc = delta.get("reasoning_content").asText();
                                    allReasoning.append(rc);
                                }
                                if (hasContent) {
                                    contentChunks[0]++;
                                    String c = delta.get("content").asText();
                                    allContent.append(c);
                                }
                                if (hasToolCalls) {
                                    tcChunks[0]++;
                                    allToolCalls.append(delta.get("tool_calls").toString());
                                }

                                // 打印前 30 个 chunk 详细信息
                                if (chunkNum[0] <= 30) {
                                    String flags = (hasReasoning ? "🧠R" : "  ")
                                                 + (hasContent ? "📝C" : "   ")
                                                 + (hasToolCalls ? "🔧T" : "   ");
                                    String rcPreview = hasReasoning
                                        ? delta.get("reasoning_content").asText() : "";
                                    String cPreview = hasContent
                                        ? delta.get("content").asText() : "";
                                    System.out.printf("#%03d [%s] R=[%s] C=[%s]%n",
                                        chunkNum[0], flags,
                                        rcPreview.length() > 60 ? rcPreview.substring(0,60)+"..." : rcPreview,
                                        cPreview.length() > 60 ? cPreview.substring(0,60)+"..." : cPreview);
                                }
                            } catch (Exception e) {}
                        });

                    // 汇总
                    System.out.println();
                    System.out.println("=== 汇总 ===");
                    System.out.println("总 chunk: " + chunkNum[0]);
                    System.out.println("reasoning_content chunks: " + reasoningChunks[0]);
                    System.out.println("content chunks:          " + contentChunks[0]);
                    System.out.println("tool_calls chunks:       " + tcChunks[0]);
                    System.out.println();
                    System.out.println("reasoning_content 总长度: " + allReasoning.length());
                    System.out.println("content 总长度:          " + allContent.length());
                    System.out.println();

                    // 检查 DSML 在哪
                    String rc = allReasoning.toString();
                    String ct = allContent.toString();
                    boolean rcHasDsml = rc.contains("<DSML") || rc.contains("<DSML");
                    boolean ctHasDsml = ct.contains("<DSML") || ct.contains("<DSML");
                    boolean tcHasName = allToolCalls.toString().contains("get_current_time");

                    System.out.println("DSML 在 reasoning_content 中: " + rcHasDsml);
                    System.out.println("DSML 在 content 中:           " + ctHasDsml);
                    System.out.println("tool_calls 中有内容:          " + tcHasName);

                    if (rcHasDsml) {
                        System.out.println();
                        System.out.println("--- reasoning_content (DSML 部分) ---");
                        int idx = rc.indexOf("<DSML");
                        if (idx < 0) idx = rc.indexOf("<DSML");
                        System.out.println(idx >= 0 ? rc.substring(idx, Math.min(idx+300, rc.length())) : "(未找到)");
                    }
                    if (ctHasDsml) {
                        System.out.println();
                        System.out.println("--- content (DSML 部分) ---");
                        int idx = ct.indexOf("<DSML");
                        if (idx < 0) idx = ct.indexOf("<DSML");
                        System.out.println(idx >= 0 ? ct.substring(idx, Math.min(idx+300, ct.length())) : "(未找到)");
                    }
                    if (tcHasName) {
                        System.out.println();
                        System.out.println("--- tool_calls ---");
                        System.out.println(allToolCalls.toString().substring(0, Math.min(500, allToolCalls.length())));
                    }
                })
                .join();
    }
}
