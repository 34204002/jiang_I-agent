package com.jiang.service;

import com.jiang.entity.Conversation;
import com.jiang.entity.Message;
import com.jiang.mapper.ConversationMapper;
import com.jiang.mapper.MessageMapper;
import com.jiang.model.req.ChatRequest;
import com.jiang.model.resp.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话服务 — Agent 核心调度（Phase 1）
 * <p>
 * 每次对话：MySQL 持久化消息 → ChatClient 调度 LLM → Redis ChatMemory 维护上下文窗口。
 * <p>
 * ChatMemory 手动管理（加载/保存），避免 Spring AI 2.0 streaming advisor 的 conversationId 丢失问题。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    private static final String MODEL = "deepseek-ai/DeepSeek-V3.2";

    /** 同步对话 */
    public ChatResponse chat(ChatRequest request) {
        Long convoId = resolveConversationId(request.getConversationId(), request.getMessage());
        String memoryKey = String.valueOf(convoId);

        saveMessage(convoId, "user", request.getMessage(), null);

        List<org.springframework.ai.chat.messages.Message> history = chatMemory.get(memoryKey);
        chatMemory.add(memoryKey, List.of(new UserMessage(request.getMessage())));

        String aiContent = chatClient.prompt()
                .messages(history)
                .user(request.getMessage())
                .call()
                .content();

        saveMessage(convoId, "assistant", aiContent, null);
        chatMemory.add(memoryKey, List.of(new AssistantMessage(aiContent)));
        updateConversationMeta(convoId);

        return ChatResponse.builder()
                .content(aiContent)
                .conversationId(String.valueOf(convoId))
                .toolsCalled(java.util.List.of())
                .build();
    }

    /** 流式对话 */
    public Flux<String> streamChat(ChatRequest request) {
        Long convoId = resolveConversationId(request.getConversationId(), request.getMessage());
        String memoryKey = String.valueOf(convoId);

        saveMessage(convoId, "user", request.getMessage(), null);

        List<org.springframework.ai.chat.messages.Message> history = chatMemory.get(memoryKey);
        chatMemory.add(memoryKey, List.of(new UserMessage(request.getMessage())));

        StringBuilder fullContent = new StringBuilder();

        return chatClient.prompt()
                .messages(history)
                .user(request.getMessage())
                .stream()
                .content()
                .doOnNext(token -> {
                    log.debug("token arrived, length={}", token.length());
                    fullContent.append(token);
                })
                .doOnComplete(() -> {
                    String content = fullContent.toString();
                    log.info("流式完成: conversationId={}, 总长度={}", convoId, content.length());
                    saveMessage(convoId, "assistant", content, null);
                    chatMemory.add(memoryKey, List.of(new AssistantMessage(content)));
                    updateConversationMeta(convoId);
                })
                .doOnError(e -> log.error("流式对话异常: conversationId={}", convoId, e));
    }

    // ==================== 内部辅助 ====================

    private Long resolveConversationId(String conversationId, String firstMessage) {
        if (conversationId != null && !conversationId.isEmpty()) {
            return Long.parseLong(conversationId);
        }
        Conversation convo = new Conversation();
        convo.setTitle(firstMessage.length() > 50 ? firstMessage.substring(0, 50) : firstMessage);
        convo.setModel(MODEL);
        convo.setMessageCount(0);
        conversationMapper.insert(convo);
        log.info("新建会话: id={}, title={}", convo.getId(), convo.getTitle());
        return convo.getId();
    }

    private void saveMessage(Long conversationId, String role, String content, String toolCalls) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolCalls(toolCalls);
        messageMapper.insert(msg);
    }

    private void updateConversationMeta(Long convoId) {
        Conversation convo = conversationMapper.selectById(convoId);
        if (convo != null) {
            convo.setMessageCount((convo.getMessageCount() != null ? convo.getMessageCount() : 0) + 2);
            conversationMapper.updateById(convo);
        }
    }
}
