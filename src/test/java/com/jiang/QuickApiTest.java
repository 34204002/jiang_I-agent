package com.jiang;

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

public class QuickApiTest {
    static final String AK = "sk-rzwpnfjvvgxfrbsmmnfilalfyeqjzwtgzczmmxocuaaqjuuu";
    static final ObjectMapper M = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        for (String scenario : new String[]{"no_tools_no_think", "no_tools_think", "tools_no_think", "tools_think"}) {
            boolean tools = scenario.contains("tools");
            boolean think = scenario.contains("think");
            System.out.println("\n=== " + scenario + " ===");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "deepseek-ai/DeepSeek-V3.2");
            body.put("messages", List.of(Map.of("role", "user", "content", "现在几点？")));
            body.put("stream", true);
            if (think) {
                body.put("enable_thinking", true);
                body.put("thinking_budget", 1024);
            }
            if (tools) body.put("tools", List.of(Map.of("type", "function", "function",
                    Map.of("name", "get_current_time", "description", "获取时间", "parameters",
                            Map.of("type", "object", "properties", Map.of())))));
            String json = M.writeValueAsString(body);
            long t0 = System.currentTimeMillis();
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.siliconflow.cn/v1/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + AK)
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                int[] n = {0};
                HttpClient.newHttpClient().sendAsync(req, HttpResponse.BodyHandlers.ofLines())
                        .thenAccept(r -> {
                            r.body().filter(l -> l.startsWith("data: ")).map(l -> l.substring(6))
                                    .takeWhile(d -> !"[DONE]".equals(d)).forEach(d -> n[0]++);
                            long dt = System.currentTimeMillis() - t0;
                            System.out.println("  OK: " + n[0] + " chunks in " + dt + "ms");
                        }).join();
            } catch (Exception e) {
                long dt = System.currentTimeMillis() - t0;
                System.out.println("  FAIL after " + dt + "ms: " + e.getMessage());
            }
        }
    }
}
