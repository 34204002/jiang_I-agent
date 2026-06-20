package com.jiang.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * SiliconFlow HTTP 传输工具——只负责发请求、收响应，不做任何业务解析。
 */
@Slf4j
@Component
public class DeepSeekStreamService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    /**
     * 同步请求，返回原始响应体字符串。
     */
    public String sync(String requestBodyJson) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.error("API 返回 {}: {}", resp.statusCode(), resp.body());
                throw new RuntimeException("API " + resp.statusCode());
            }
            return resp.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("同步请求失败", e);
            throw new RuntimeException("AI 调用失败: " + e.getMessage());
        }
    }

    /**
     * 流式请求，返回原始 SSE data 行（已剥离 {@code data: } 前缀，q止于 {@code [DONE]}）。
     */
    public Flux<String> stream(String requestBodyJson) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                .build();

        return Mono.fromFuture(httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines()))
                .flatMapMany(response -> {
                    if (response.statusCode() != 200) {
                        String err = response.body().findFirst().orElse("(empty body)");
                        log.error("API 返回 {}: {}", response.statusCode(), err);
                        return Flux.error(new RuntimeException("API " + response.statusCode() + ": " + err));
                    }
                    return Flux.fromStream(response.body()
                                    .filter(line -> line.startsWith("data: "))
                                    .map(line -> line.substring(6)))
                            .takeWhile(data -> !"[DONE]".equals(data));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
