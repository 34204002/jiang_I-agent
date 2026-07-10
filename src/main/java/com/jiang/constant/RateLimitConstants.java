package com.jiang.constant;

/**
 * 令牌桶限流相关常量
 */
public final class RateLimitConstants {
    /**
     * 令牌桶容量
     */
    public static final int BUCKET_CAPACITY = 30;
    /**
     * 令牌填充速率（每秒）
     */
    public static final int REFILL_RATE = 30;
    /**
     * 闲置桶清理间隔（毫秒）
     */
    public static final long EVICT_INTERVAL_MS = 600_000;

    private RateLimitConstants() {
    }
}
