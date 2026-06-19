package com.jiang.service;

import com.jiang.entity.AgentConfig;
import com.jiang.entity.Conversation;
import com.jiang.entity.Message;
import com.jiang.mapper.AgentConfigMapper;
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
 * 对话服务 — Agent 核心调度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AgentConfigMapper agentConfigMapper;

    private static final String DEFAULT_MODEL = "deepseek-ai/DeepSeek-V3.2";

    /**
     * 从 DB 读取自定义系统提示词；无自定义时返回 null，由 ChatClient 默认值兜底
     */
    private String getDbSystemPrompt() {
        try {
            AgentConfig config = agentConfigMapper.selectById(1);
            if (config != null && config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
                return config.getSystemPrompt();
            }
        } catch (Exception e) {
            log.debug("DB AgentConfig 读取失败，使用 ChatClient 默认提示词", e);
        }
        return null;
    }

    private String getModel() {
        try {
            AgentConfig config = agentConfigMapper.selectById(1);
            if (config != null && config.getModel() != null && !config.getModel().isBlank()) {
                return config.getModel();
            }
        } catch (Exception ignored) {}
        return DEFAULT_MODEL;
    }

    // ==================== 公开接口 ====================

    /** 同步对话 */
    public ChatResponse chat(ChatRequest request, Long userId) {
        Long convoId = resolveConversationId(request.getConversationId(), request.getMessage(), userId);
        String memoryKey = String.valueOf(convoId);

        saveMessage(convoId, "user", request.getMessage(), null);

        var history = chatMemory.get(memoryKey);
        chatMemory.add(memoryKey, List.of(new UserMessage(request.getMessage())));

        String aiContent = doCall(history, request.getMessage());
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
    public Flux<String> streamChat(ChatRequest request, Long userId) {
        Long convoId = resolveConversationId(request.getConversationId(), request.getMessage(), userId);
        String memoryKey = String.valueOf(convoId);

        saveMessage(convoId, "user", request.getMessage(), null);

        var history = chatMemory.get(memoryKey);
        chatMemory.add(memoryKey, List.of(new UserMessage(request.getMessage())));

        return doStream(history, request.getMessage(), convoId, memoryKey);
    }

    // ==================== LLM 调用模板 ====================

    /** 组装 Prompt — 默认 system 已由 ChatClient bean 预置，仅在 DB 有自定义时覆盖 */
    private ChatClient.ChatClientRequestSpec spec(List<org.springframework.ai.chat.messages.Message> history, String userMsg) {
        var s = chatClient.prompt().messages(history).user(userMsg);
        String dbPrompt = getDbSystemPrompt();
        if (dbPrompt != null) {
            s.system(dbPrompt);
        }
        return s;
    }

    /** 同步调用 */
    private String doCall(List<org.springframework.ai.chat.messages.Message> history, String userMsg) {
        return spec(history, userMsg).call().content();
    }

    /** 流式调用 + 统一收尾 */
    private Flux<String> doStream(List<org.springframework.ai.chat.messages.Message> history,
                                   String userMsg, Long convoId, String memoryKey) {
        StringBuilder full = new StringBuilder();

        return spec(history, userMsg)
                .stream()
                .content()
                .doOnNext(full::append)
                .doOnComplete(() -> {
                    String content = full.toString();
                    log.info("流式完成: conversationId={}, 总长度={}", convoId, content.length());
                    saveMessage(convoId, "assistant", content, null);
                    chatMemory.add(memoryKey, List.of(new AssistantMessage(content)));
                    updateConversationMeta(convoId);
                })
                .doOnError(e -> log.error("流式对话异常: conversationId={}", convoId, e));
    }

    // ==================== 内部辅助 ====================

    private Long resolveConversationId(String conversationId, String firstMessage, Long userId) {
        if (conversationId != null && !conversationId.isEmpty()) {
            Long id = Long.parseLong(conversationId);
            Conversation existing = conversationMapper.selectById(id);
            if (existing == null) {
                log.warn("会话不存在: id={}, 将创建新会话", id);
            } else if (!existing.getUserId().equals(userId)) {
                log.warn("越权访问会话: userId={}, conversationId={}, owner={}",
                        userId, id, existing.getUserId());
                throw new SecurityException("无权访问该会话");
            } else {
                return id;
            }
        }
        Conversation convo = new Conversation();
        convo.setUserId(userId);
        convo.setTitle(firstMessage.length() > 50 ? firstMessage.substring(0, 50) : firstMessage);
        convo.setModel(getModel());
        convo.setMessageCount(0);
        conversationMapper.insert(convo);
        log.info("新建会话: id={}, userId={}, title={}", convo.getId(), userId, convo.getTitle());
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
