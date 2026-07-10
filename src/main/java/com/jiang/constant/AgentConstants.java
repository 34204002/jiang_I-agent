package com.jiang.constant;

/**
 * Agent 行为相关常量
 */
public final class AgentConstants {
    /**
     * 工具调用最大轮数
     */
    public static final int MAX_TOOL_ROUNDS = 10;
    /**
     * Redis ChatMemory 默认 TTL（分钟）
     */
    public static final int MEMORY_TTL_MINUTES = 30;

    private AgentConstants() {
    }
}
