package com.jiang;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 验证 deepseek-v4-flash 是否默认返回 reasoning_content（不传 thinking 参数）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class V4DefaultThinkingTest {

    private static final Logger log = LoggerFactory.getLogger(V4DefaultThinkingTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    // ==================== 不传 thinking，查是否有 reasoning_content ====================

    @Test
    void testV4FlashWithoutThinkingParam() throws Exception {
        log.info("=== v4-flash 不传 thinking 参数 ===");

        String body = MAPPER.writeValueAsString(Map.of(
                "model", "deepseek-v4-flash",
                "messages", List.of(Map.of("role", "user", "content", "用Java写一个单例模式")),
                "stream", false));

        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        var req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        // 直接搜原始 JSON
        boolean hasRc = resp.body().contains("reasoning_content");
        var completion = MAPPER.readValue(resp.body(), ChatCompletion.class);
        var msg = completion.choices().get(0).message();
        String rc = msg.reasoningContent();
        int rcLen = rc != null ? rc.length() : 0;

        log.info("原始JSON含reasoning_content: {}", hasRc);
        log.info("类型反序列化后: {} chars", rcLen);
        log.info("content 前100字: {}", msg.content() != null ? msg.content().substring(0, Math.min(100, msg.content().length())) : "(空)");

        if (hasRc && rcLen > 0) {
            log.info("→ 🟢 v4-flash 默认返回思考内容！不需要手动传 thinking 参数");
            log.info("思考前150字: {}", rc.substring(0, Math.min(150, rcLen)));
        } else if (hasRc && rcLen == 0) {
            log.info("→ 🟡 JSON 中有 reasoning_content 字段但为空（思考了但不返回？）");
        } else {
            log.info("→ 🔴 v4-flash 不传 thinking 不返回思考内容");
        }
    }
}
