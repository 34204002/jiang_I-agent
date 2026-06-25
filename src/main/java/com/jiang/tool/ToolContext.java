package com.jiang.tool;

/**
 * 工具调用上下文 — 通过 ThreadLocal 传递当前用户 ID 和会话 ID。
 * ChatService 在调用工具前设置，工具执行后清理。
 */
public class ToolContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_CONVERSATION = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_REASONING = new ThreadLocal<>();

    public static void setUser(Long userId) { CURRENT_USER.set(userId); }
    public static Long getUser() { return CURRENT_USER.get(); }
    public static void setConversation(Long convoId) { CURRENT_CONVERSATION.set(convoId); }
    public static Long getConversation() { return CURRENT_CONVERSATION.get(); }
    public static void setReasoning(String rc) { CURRENT_REASONING.set(rc); }
    public static String getReasoning() { return CURRENT_REASONING.get(); }

    public static void clear() {
        CURRENT_USER.remove();
        CURRENT_CONVERSATION.remove();
        CURRENT_REASONING.remove();
    }
}
