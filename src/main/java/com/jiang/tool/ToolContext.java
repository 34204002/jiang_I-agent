package com.jiang.tool;

/**
 * 工具调用上下文 — 通过 ThreadLocal 传递当前用户 ID 和会话 ID。
 * ChatService 在调用工具前设置，工具执行后清理。
 */
public class ToolContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_CONVERSATION = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_REASONING = new ThreadLocal<>();

    public static Long getUser() {
        return CURRENT_USER.get();
    }

    public static void setUser(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getConversation() {
        return CURRENT_CONVERSATION.get();
    }

    public static void setConversation(Long convoId) {
        CURRENT_CONVERSATION.set(convoId);
    }

    public static String getReasoning() {
        return CURRENT_REASONING.get();
    }

    public static void setReasoning(String rc) {
        CURRENT_REASONING.set(rc);
    }

    public static void clear() {
        CURRENT_USER.remove();
        CURRENT_CONVERSATION.remove();
        CURRENT_REASONING.remove();
    }

    /**
     * 在指定上下文中执行动作：先设置 ThreadLocal，执行完毕后恢复原值。
     * <p>
     * 用于 Reactor 流式场景——工具可能在 boundedElastic 的<b>不同工作线程</b>上执行，
     * ThreadLocal 不会跨线程自动传播，因此必须在执行前于当前线程重设，
     * 结束后恢复原值以避免把残留值泄漏给被复用的线程。
     */
    public static <T> T runWithContext(Long userId, Long conversationId, String reasoning,
                                       java.util.function.Supplier<T> action) {
        Long prevUser = CURRENT_USER.get();
        Long prevConvo = CURRENT_CONVERSATION.get();
        String prevReasoning = CURRENT_REASONING.get();
        if (userId != null) CURRENT_USER.set(userId);
        if (conversationId != null) CURRENT_CONVERSATION.set(conversationId);
        if (reasoning != null) CURRENT_REASONING.set(reasoning);
        try {
            return action.get();
        } finally {
            CURRENT_USER.set(prevUser);
            CURRENT_CONVERSATION.set(prevConvo);
            CURRENT_REASONING.set(prevReasoning);
        }
    }
}
