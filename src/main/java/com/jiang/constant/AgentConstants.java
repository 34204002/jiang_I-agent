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
    /**
     * 触发对话摘要的消息数阈值（约 20 轮对话）
     */
    public static final int SUMMARY_THRESHOLD = 40;
    /**
     * 摘要后保留的最近消息条数
     */
    public static final int KEEP_RECENT = 20;

    private AgentConstants() {
    }
}
