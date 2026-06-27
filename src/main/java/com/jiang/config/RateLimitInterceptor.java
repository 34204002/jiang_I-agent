package com.jiang.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌桶限流拦截器（Bucket4j 实现）。
 * <p>
 * 按 userId 分桶，每个用户独立限流。未登录请求按 IP 限流。
 * 容量 30 tokens，填充速率 ~1 token/s（≈60 req/min，峰值 30 并发）。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(30)
                        .refillGreedy(30, Duration.ofSeconds(30))
                        .build())
                .build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 按 userId 分桶，未登录按 IP
        Object uid = request.getAttribute("userId");
        String key = uid != null ? "u:" + uid : "ip:" + request.getRemoteAddr();

        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());

        if (bucket.tryConsume(1)) {
            return true;
        }

        log.warn("限流触发: key={}, uri={}", key, request.getRequestURI());
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
        return false;
    }

    /** 每 10 分钟清理闲置桶（已满的 bucket 视为不活跃用户） */
    @Scheduled(fixedRate = 600_000)
    public void evict() {
        int before = buckets.size();
        buckets.values().removeIf(b -> b.getAvailableTokens() >= 30);
        log.debug("限流桶清理: {} → {}", before, buckets.size());
    }
}
