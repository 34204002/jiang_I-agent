package com.jiang.service;

import com.jiang.entity.Conversation;
import com.jiang.entity.Message;
import com.jiang.mapper.ConversationMapper;
import com.jiang.mapper.MessageMapper;
import com.jiang.model.resp.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 对话服务 — Agent 核心调度（Phase 1）
 * <p>
 * 每次对话：MySQL 持久化消息 → ChatClient 调度 LLM → Redis ChatMemory 维护上下文窗口。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    private static final String MODEL = "deepseek-ai/DeepSeek-V3.2";

    /**
     * 同步对话 — 发送消息，等待完整回复
     *
     * @param message        用户消息
     * @param conversationId 会话 ID（null 则自动创建）
     * @return AI 回复 + 会话 ID + 工具调用记录
     */
    public ChatResponse chat(String message, String conversationId) {
        // 1. 解析会话：无则创建
        Long convoId = resolveConversationId(conversationId, message);

        // 2. 持久化用户消息
        saveMessage(convoId, "user", message, null);

        // 3. 调用 LLM（ChatMemory advisor 自动加载历史上下文）
        String aiContent = chatClient.prompt()
                .user(message)
                .advisors(a -> a.param("chat_memory_conversation_id", String.valueOf(convoId)))
                .call()
                .content();

        // 4. 持久化 AI 回复
        saveMessage(convoId, "assistant", aiContent, null);

        // 5. 更新会话元数据
        updateConversationMeta(convoId);

        return ChatResponse.builder()
                .content(aiContent)
                .conversationId(String.valueOf(convoId))
                .toolsCalled(java.util.List.of())
                .build();
    }

    /**
     * 流式对话 — 每个 token 实时推送
     *
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @return token 流（SSE 由 Controller 转换为 SseEmitter）
     */
    public Flux<String> streamChat(String message, String conversationId) {
        // 1. 解析会话
        Long convoId = resolveConversationId(conversationId, message);

        // 2. 持久化用户消息
        saveMessage(convoId, "user", message, null);

        // 3. 流式调用 LLM + 拼装完整回复用于持久化
        StringBuilder fullContent = new StringBuilder();

        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param("chat_memory_conversation_id", String.valueOf(convoId)))
                .stream()
                .content()
                .doOnNext(token -> fullContent.append(token))
                .doOnComplete(() -> {
                    // 4. 流结束后持久化 AI 回复
                    saveMessage(convoId, "assistant", fullContent.toString(), null);
                    updateConversationMeta(convoId);
                })
                .doOnError(e -> log.error("流式对话异常: conversationId={}", convoId, e));
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 解析 conversationId：为空则新建会话
     */
    private Long resolveConversationId(String conversationId, String firstMessage) {
        if (conversationId != null && !conversationId.isEmpty()) {
            return Long.parseLong(conversationId);
        }
        // 新建会话
        Conversation convo = new Conversation();
        convo.setTitle(firstMessage.length() > 50 ? firstMessage.substring(0, 50) : firstMessage);
        convo.setModel(MODEL);
        convo.setMessageCount(0);
        conversationMapper.insert(convo);
        log.info("新建会话: id={}, title={}", convo.getId(), convo.getTitle());
        return convo.getId();
    }

    /**
     * 持久化一条消息到 MySQL
     */
    private void saveMessage(Long conversationId, String role, String content, String toolCalls) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolCalls(toolCalls);
        messageMapper.insert(msg);
    }

    /**
     * 更新会话 message_count 和 updated_at
     */
    private void updateConversationMeta(Long convoId) {
        Conversation convo = conversationMapper.selectById(convoId);
        if (convo != null) {
            convo.setMessageCount((convo.getMessageCount() != null ? convo.getMessageCount() : 0) + 2);
            conversationMapper.updateById(convo);
        }
    }
}
