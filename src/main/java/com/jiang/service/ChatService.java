package com.jiang.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiang.entity.AgentConfig;
import com.jiang.entity.Conversation;
import com.jiang.mapper.AgentConfigMapper;
import com.jiang.mapper.ConversationMapper;
import com.jiang.mapper.MessageMapper;
import com.jiang.model.req.ChatRequest;
import com.jiang.model.resp.ChatResponse;
import com.jiang.util.DeepSeekStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话服务 — Agent 核心调度。
 * 请求构建、SSE 解析、消息持久化全部在此；HTTP 传输委托给 {@link DeepSeekStreamService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final DeepSeekStreamService apiClient;
    private final ChatMemory chatMemory;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AgentConfigMapper agentConfigMapper;
    private final ObjectMapper objectMapper;

    @Qualifier("defaultSystemPrompt")
    private final String defaultSystemPrompt;

    @Value("${spring.ai.openai.chat.model}")
    private String defaultModel;

    // ==================== 提示词 / 模型读取 ====================

    private String getDbSystemPrompt() {
        try {
            AgentConfig config = agentConfigMapper.selectById(1);
            if (config != null && config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
                return config.getSystemPrompt();
            }
        } catch (Exception e) {
            log.debug("DB AgentConfig 读取失败", e);
        }
        return null;
    }

    private String getSystemPrompt() {
        String db = getDbSystemPrompt();
        return db != null ? db : defaultSystemPrompt;
    }

    private String getModel() {
        try {
            AgentConfig config = agentConfigMapper.selectById(1);
            if (config != null && config.getModel() != null && !config.getModel().isBlank()) {
                return config.getModel();
            }
        } catch (Exception ignored) {}
        return defaultModel;
    }

    // ==================== 公开接口 ====================

    /** 同步对话 */
    public ChatResponse chat(ChatRequest request, Long userId) {
        var ctx = prepareConversation(request, userId);

        String aiContent = doCall(ctx.history, request.getMessage());
        saveAssistantMessage(ctx, aiContent);

        return ChatResponse.builder()
                .content(aiContent)
                .conversationId(String.valueOf(ctx.convoId))
                .toolsCalled(List.of())
                .build();
    }

    /** 普通流式对话 */
    public Flux<String> streamChat(ChatRequest request, Long userId) {
        var ctx = prepareConversation(request, userId);
        return doStream(ctx.history, request.getMessage(), ctx.convoId, ctx.memoryKey);
    }

    /** 思考模式流式对话 */
    public Flux<String> streamChatWithThinking(ChatRequest request, Long userId) {
        var ctx = prepareConversation(request, userId);
        return doStreamThinking(ctx.history, request.getMessage(), ctx.convoId, ctx.memoryKey);
    }

    // ==================== 会话准备 & 持久化辅助 ====================

    /** 流式/同步共用的会话准备工作：解析/创建会话、保存用户消息、加载历史 */
    private ConversationContext prepareConversation(ChatRequest request, Long userId) {
        Long convoId = resolveConversationId(request.getConversationId(), request.getMessage(), userId);
        String memoryKey = String.valueOf(convoId);
        saveMessage(convoId, "user", request.getMessage(), null);
        var history = chatMemory.get(memoryKey);
        chatMemory.add(memoryKey, List.of(new UserMessage(request.getMessage())));
        return new ConversationContext(convoId, memoryKey, history);
    }

    /** 保存 assistant 消息到 DB 和 ChatMemory，更新会话计数 */
    private void saveAssistantMessage(ConversationContext ctx, String content) {
        saveMessage(ctx.convoId, "assistant", content, null);
        chatMemory.add(ctx.memoryKey, List.of(new AssistantMessage(content)));
        updateConversationMeta(ctx.convoId);
    }

    private record ConversationContext(Long convoId, String memoryKey, List<Message> history) {}

    /** 流式路径通用的 assistant 持久化：DB + ChatMemory + 更新计数 */
    private void persistAssistant(Long convoId, String memoryKey, String content) {
        saveMessage(convoId, "assistant", content, null);
        chatMemory.add(memoryKey, List.of(new AssistantMessage(content)));
        updateConversationMeta(convoId);
    }

    // ==================== LLM 调用 ====================

    /** 同步调用 */
    private String doCall(List<Message> history, String userMsg) {
        String body = buildRequestBody(history, userMsg, false);
        String resp = apiClient.sync(body);
        try {
            return objectMapper.readTree(resp)
                    .path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("解析同步响应失败", e);
        }
    }

    /** 普通流式 */
    private Flux<String> doStream(List<Message> history, String userMsg,
                                   Long convoId, String memoryKey) {
        StringBuilder full = new StringBuilder();
        String body = buildRequestBody(history, userMsg, false);

        return apiClient.stream(body)
                .<String>handle((data, sink) -> {
                    String content = extractDeltaContent(data);
                    if (content != null && !content.isEmpty()) {
                        full.append(content);
                        try {
                            sink.next("{\"type\":\"content\",\"content\":"
                                    + objectMapper.writeValueAsString(content) + "}");
                        } catch (Exception ignored) {}
                    }
                })
                .doOnComplete(() -> {
                    String content = full.toString();
                    log.info("流式完成: conversationId={}, 总长度={}", convoId, content.length());
                    persistAssistant(convoId, memoryKey, content);
                })
                .doOnError(e -> {
                    log.warn("流式中断: conversationId={}, 已累积={}", convoId, full.length());
                    if (full.length() > 0) persistAssistant(convoId, memoryKey, full.toString());
                })
                .onErrorResume(e ->
                        Flux.just("{\"type\":\"content\",\"content\":\"\\n\\n（AI 回复因网络原因中断，请重试）\"}"));
    }

    /** 思考模式流式：请求构建 → apiClient.stream() → 解析 delta.reasoning_content + delta.content */
    private Flux<String> doStreamThinking(List<Message> history, String userMsg,
                                          Long convoId, String memoryKey) {
        StringBuilder thinkingBuf = new StringBuilder();
        StringBuilder contentBuf = new StringBuilder();
        String body = buildRequestBody(history, userMsg, true);

        return apiClient.stream(body)
                .<String>handle((data, sink) -> {
                    try {
                        JsonNode root = objectMapper.readTree(data);
                        JsonNode delta = root.path("choices").get(0).path("delta");
                        String reasoning = delta.has("reasoning_content")
                                ? delta.get("reasoning_content").asText() : null;
                        String content = delta.has("content")
                                ? delta.get("content").asText() : null;

                        if (reasoning != null && !reasoning.isEmpty()) {
                            thinkingBuf.append(reasoning);
                            sink.next("{\"type\":\"thinking\",\"content\":"
                                    + objectMapper.writeValueAsString(reasoning) + "}");
                        }
                        if (content != null && !content.isEmpty()) {
                            contentBuf.append(content);
                            sink.next("{\"type\":\"content\",\"content\":"
                                    + objectMapper.writeValueAsString(content) + "}");
                        }
                    } catch (Exception ignored) {}
                })
                .doOnComplete(() -> {
                    String thinking = thinkingBuf.toString();
                    String answer = contentBuf.toString();
                    log.info("思考模式流式完成: conversationId={}, 思考长度={}, 回答长度={}",
                            convoId, thinking.length(), answer.length());
                    String full = thinking.isEmpty() ? answer
                            : "<thinking>" + thinking + "</thinking>\n" + answer;
                    saveMessage(convoId, "assistant", full, null);
                    chatMemory.add(memoryKey, List.of(new AssistantMessage(answer)));
                    updateConversationMeta(convoId);
                })
                .doOnError(e -> {
                    log.warn("思考模式流式中断: conversationId={}", convoId);
                    if (contentBuf.length() > 0)
                        persistAssistant(convoId, memoryKey, contentBuf.toString());
                })
                .onErrorResume(e ->
                        Flux.just("{\"type\":\"content\",\"content\":\"\\n\\n（AI 回复因网络原因中断，请重试）\"}"));
    }

    // ==================== 请求体 & SSE 解析 ====================

    /**
     * 构建 OpenAI 兼容 JSON 请求体。
     * @param thinking 是否开启思考模式（R1 系列忽略，传了会 400）
     */
    private String buildRequestBody(List<Message> history, String userMsg, boolean thinking) {
        List<Map<String, Object>> messages = new ArrayList<>();
        String systemPrompt = getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        if (history != null) {
            for (Message msg : history) {
                messages.add(Map.of("role",
                        msg.getMessageType().name().toLowerCase(),
                        "content", msg.getText()));
            }
        }
        messages.add(Map.of("role", "user", "content", userMsg != null ? userMsg : ""));

        String model = getModel();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);
        if (thinking && (model == null || !model.contains("R1"))) {
            body.put("enable_thinking", true);
            body.put("thinking_budget", 1024);
        }
        try {
            // 用干净的 ObjectMapper，避免 Redis DefaultTyping 污染 JSON
            String json = new ObjectMapper().writeValueAsString(body);
            log.info("API请求: model={}, thinking={}, body={}", model, thinking, json);
            return json;
        } catch (Exception e) {
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    /** 从 SSE chunk 提取 delta.content（普通流式用） */
    private String extractDeltaContent(String data) {
        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode delta = node.path("choices").get(0).path("delta");
            if (delta.has("content") && !delta.get("content").isNull()) {
                return delta.get("content").asText();
            }
        } catch (Exception ignored) {}
        return null;
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
        com.jiang.entity.Message msg = new com.jiang.entity.Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolCalls(toolCalls);
        messageMapper.insert(msg);
    }

    /**
     * 更新会话元数据（消息计数 +2）。假定每次调用对应一对 user+assistant 消息。
     */
    private void updateConversationMeta(Long convoId) {
        Conversation convo = conversationMapper.selectById(convoId);
        if (convo != null) {
            convo.setMessageCount((convo.getMessageCount() != null ? convo.getMessageCount() : 0) + 2);
            conversationMapper.updateById(convo);
        }
    }
}
