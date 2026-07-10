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
 * 模拟项目实际请求：12 个工具 + enable_thinking + 中文 System Prompt。
 */
public class FullToolTest {

    static final String AK = "sk-rzwpnfjvvgxfrbsmmnfilalfyeqjzwtgzczmmxocuaaqjuuu";
    static final String URL = "https://api.siliconflow.cn/v1";
    static final ObjectMapper M = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        // 和项目一样的 System Prompt 摘要
        String system = "你是 Jiang I-Agent，个人AI助手。可以调用工具。时间盲区：你不知道当前时间，需要时调用 get_current_time。";

        // 12 个工具的简化 Schema（和 ToolRegistry 生成的格式一样）
        var tools = List.of(
                tool("create_todo", "创建待办", props(List.of("title"), List.of("dueDate"))),
                tool("list_todos", "查询待办列表", props(List.of("status"), List.of())),
                tool("complete_todo", "完成待办", props(List.of("todoId"), List.of())),
                tool("delete_todo", "删除待办", props(List.of("todoId"), List.of())),
                tool("get_current_time", "获取当前时间", props(List.of(), List.of())),
                tool("search_knowledge", "搜索知识库", props(List.of("query"), List.of("topK"))),
                tool("read_web_page", "读取网页", props(List.of("url"), List.of())),
                tool("create_reminder", "创建提醒", props(List.of("message", "remindTime"), List.of())),
                tool("list_reminders", "查看提醒", props(List.of("status"), List.of())),
                tool("cancel_reminder", "取消提醒", props(List.of("reminderId"), List.of())),
                tool("search_conversation", "搜索对话", props(List.of("keyword"), List.of("limit"))),
                tool("export_conversation", "导出对话", props(List.of(), List.of()))
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "deepseek-ai/DeepSeek-V3.2");
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", "现在几点？")
        ));
        body.put("stream", true);
        body.put("enable_thinking", true);
        body.put("thinking_budget", 1024);
        body.put("tools", tools);

        System.out.println("=== 12 工具 + enable_thinking（模拟实际请求）===");
        System.out.println("bodyLen=" + M.writeValueAsString(body).length());
        System.out.println();

        int[] rcN = {0}, ctN = {0}, tcN = {0};
        StringBuilder rcB = new StringBuilder(), ctB = new StringBuilder(), tcB = new StringBuilder();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(URL + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + AK)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(r -> {
                    r.body().filter(l -> l.startsWith("data: "))
                            .map(l -> l.substring(6))
                            .takeWhile(d -> !"[DONE]".equals(d))
                            .forEach(d -> {
                                try {
                                    JsonNode root = M.readTree(d);
                                    JsonNode choice = root.path("choices").get(0);
                                    JsonNode delta = choice.path("delta");
                                    String fr = choice.path("finish_reason").asText("");

                                    boolean hr = delta.has("reasoning_content") && !delta.get("reasoning_content").isNull() && !delta.get("reasoning_content").asText().isEmpty();
                                    boolean hc = delta.has("content") && !delta.get("content").isNull() && !delta.get("content").asText().isEmpty();
                                    boolean ht = delta.has("tool_calls") && !delta.get("tool_calls").isNull();

                                    if (hr) {
                                        rcN[0]++;
                                        rcB.append(delta.get("reasoning_content").asText());
                                    }
                                    if (hc) {
                                        ctN[0]++;
                                        ctB.append(delta.get("content").asText());
                                    }
                                    if (ht) {
                                        tcN[0]++;
                                        tcB.append(delta.get("tool_calls").toString());
                                    }

                                    String kind = hr ? "R" : hc ? "C" : ht ? "T" : ".";
                                    String detail = "";
                                    if (hr) detail = delta.get("reasoning_content").asText();
                                    else if (hc) detail = delta.get("content").asText();
                                    else if (ht) detail = delta.get("tool_calls").toString();
                                    if (!fr.isEmpty()) kind += "(" + fr + ")";

                                    System.out.printf("[%s] %s%n", kind, detail.isEmpty() ? "(empty)" :
                                            detail.length() > 120 ? detail.substring(0, 120) + "..." : detail);
                                } catch (Exception e) {
                                    System.out.println("PARSE ERROR: " + e.getMessage());
                                }
                            });

                    String ct = ctB.toString();
                    System.out.println();
                    System.out.println("=== 结果 ===");
                    System.out.println("reasoning_content chunks: " + rcN[0] + " 长度: " + rcB.length());
                    System.out.println("content chunks: " + ctN[0] + " 长度: " + ct.length());
                    System.out.println("tool_calls chunks: " + tcN[0]);
                    System.out.println("DSML in content: " + (ct.contains("<DSML") || ct.contains("<DSML")));
                    System.out.println("tool_calls 有效: " + tcB.toString().contains("get_current_time"));

                    if (ct.contains("<DSML") || ct.contains("<DSML")) {
                        int i = Math.max(ct.indexOf("<DSML"), ct.indexOf("<DSML"));
                        System.out.println("--- DSML ---");
                        System.out.println(ct.substring(i, Math.min(i + 300, ct.length())));
                    }
                    System.out.println("--- tool_calls ---");
                    System.out.println(tcB.substring(0, Math.min(500, tcB.length())));
                })
                .join();
    }

    static Map<String, Object> tool(String name, String desc, Map<String, Object> params) {
        return Map.of("type", "function",
                "function", Map.of("name", name, "description", desc, "parameters", params));
    }

    static Map<String, Object> props(List<String> required, List<String> optional) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (String r : required) props.put(r, Map.of("type", "string", "description", r));
        for (String o : optional) props.put(o, Map.of("type", "string", "description", o));
        return Map.of("type", "object", "properties", props,
                "required", required);
    }
}
