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
import com.jiang.tool.DsmlParser;
import com.jiang.tool.ToolContext;
import com.jiang.tool.ToolRegistry;
import com.jiang.util.DeepSeekStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话服务 — Agent 核心调度。
 * 请求构建、SSE 解析、工具调用、消息持久化全部在此；HTTP 传输委托给 {@link DeepSeekStreamService}。
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
    private final VectorStore vectorStore;
    private final ToolRegistry toolRegistry;

    @Qualifier("defaultSystemPrompt")
    private final String defaultSystemPrompt;

    @Value("${spring.ai.openai.chat.model}")
    private String defaultModel;

    private static final int MAX_TOOL_ROUNDS = 5;
    private static final ObjectMapper CLEAN_MAPPER = new ObjectMapper();

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
        String aiContent = doCall(ctx.history, request.getMessage(), userId, ctx.convoId);
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
        ToolContext.setUser(userId);
        ToolContext.setConversation(ctx.convoId);
        return streamWithPossibleTools(ctx.history, request.getMessage(),
                ctx.convoId, ctx.memoryKey, false, userId)
                .doFinally(s -> ToolContext.clear());
    }

    /** 思考模式流式对话 */
    public Flux<String> streamChatWithThinking(ChatRequest request, Long userId) {
        var ctx = prepareConversation(request, userId);
        ToolContext.setUser(userId);
        ToolContext.setConversation(ctx.convoId);
        return streamWithPossibleTools(ctx.history, request.getMessage(),
                ctx.convoId, ctx.memoryKey, true, userId)
                .doFinally(s -> ToolContext.clear());
    }

    // ==================== 会话准备 & 持久化辅助 ====================

    private ConversationContext prepareConversation(ChatRequest request, Long userId) {
        Long convoId = resolveConversationId(request.getConversationId(), request.getMessage(), userId);
        String memoryKey = String.valueOf(convoId);
        saveMessage(convoId, "user", request.getMessage(), null);
        var history = chatMemory.get(memoryKey);
        chatMemory.add(memoryKey, List.of(new UserMessage(request.getMessage())));
        return new ConversationContext(convoId, memoryKey, history);
    }

    private void saveAssistantMessage(ConversationContext ctx, String content) {
        saveMessage(ctx.convoId, "assistant", content, null);
        chatMemory.add(ctx.memoryKey, List.of(new AssistantMessage(content)));
        updateConversationMeta(ctx.convoId);
    }

    private record ConversationContext(Long convoId, String memoryKey, List<Message> history) {}

    private void persistAssistant(Long convoId, String memoryKey, String content) {
        saveMessage(convoId, "assistant", content, null);
        chatMemory.add(memoryKey, List.of(new AssistantMessage(content)));
        updateConversationMeta(convoId);
    }

    // ==================== LLM 调用 ====================

    /** 同步调用 */
    private String doCall(List<Message> history, String userMsg, Long userId, Long convoId) {
        String body = buildRequestBody(history, userMsg, false);
        String resp = apiClient.sync(body);
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode msg = root.path("choices").get(0).path("message");
            // 处理工具调用
            if (msg.has("tool_calls") && !msg.get("tool_calls").isNull()) {
                String answer = runToolLoop(history, userMsg, msg.get("tool_calls"), userId, convoId);
                return answer;
            }
            return msg.path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("解析同步响应失败", e);
        }
    }

    /**
     * 统一流式入口：先流式调用 LLM，如果模型返回 tool_calls 则在流结束后执行工具并链路调用。
     */
    private Flux<String> streamWithPossibleTools(List<Message> history, String userMsg,
                                                   Long convoId, String memoryKey, boolean thinking,
                                                   Long userId) {
        // Mutable accumulators
        final String[] tcName = {null};
        final StringBuilder tcArgs = new StringBuilder();
        final StringBuilder contentBuf = new StringBuilder();  // 持久化用的完整文本
        final StringBuilder thinkingBuf = thinking ? new StringBuilder() : null;
        final StringBuilder batchBuf = new StringBuilder();    // 微批量缓冲，攒够再 flush
        final String[] tagPrefix = {""};                       // 跨批次标签前缀残留
        final String[] dsmlPending = {""};                   // 跨批次 DSML 未完成块暂存

        String body = buildRequestBody(history, userMsg, thinking);

        return apiClient.stream(body)
                .<String>handle((data, sink) -> {
                    try {
                        JsonNode root = new ObjectMapper().readTree(data);
                        JsonNode delta = root.path("choices").get(0).path("delta");

                        // === OpenAI 标准 tool_calls（兼容未来） ===
                        JsonNode tcArray = delta.path("tool_calls");
                        if (tcArray.isArray() && tcArray.size() > 0) {
                            JsonNode tc = tcArray.get(0);
                            JsonNode func = tc.path("function");
                            if (func.has("name") && !func.get("name").isNull()) {
                                String n = func.get("name").asText();
                                if (n != null && !n.isEmpty()) tcName[0] = n;
                            }
                            if (func.has("arguments")) {
                                tcArgs.append(func.get("arguments").asText());
                            }
                            // 有实质 name 才是真正的 tool call；空 name 是 DeepSeek 的占位，
                            // 不要 return，让 DSML 路径处理 content
                            if (tcName[0] != null) return;
                        }

                        // Thinking mode: reasoning_content
                        if (thinking) {
                            if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                                String reasoning = delta.get("reasoning_content").asText();
                                if (!reasoning.isEmpty()) {
                                    thinkingBuf.append(reasoning);
                                    sink.next("{\"type\":\"thinking\",\"content\":"
                                            + CLEAN_MAPPER.writeValueAsString(reasoning) + "}");
                                }
                            }
                        }

                        // === Content: 微批量缓冲 → 攒到句子边界再 flush ===
                        if (delta.has("content") && !delta.get("content").isNull()) {
                            String c = delta.get("content").asText();
                            if (c.isEmpty()) return;
                            batchBuf.append(c);
                            // 攒够 200 字符 或 遇到句子边界（。！？\n）就 flush
                            if (batchBuf.length() >= 200 || c.matches(".*[。！？\n]") || c.matches(".*[.!?\n]")) {
                                flushBatch(batchBuf, contentBuf, sink, tcName, tcArgs, tagPrefix, dsmlPending);
                            }
                        }
                    } catch (Exception ignored) {}
                })
                .concatWith(Flux.<String>defer(() -> {
                    // 流结束：处理 batchBuf + dsmlPending 中残留的内容
                    if ((batchBuf.length() > 0 || !dsmlPending[0].isEmpty())
                            && (tcName[0] == null || tcName[0].isEmpty())) {
                        String tail = dsmlPending[0] + tagPrefix[0] + batchBuf.toString();
                        int idx = DsmlParser.indexOf(tail);
                        if (idx >= 0) {
                            String before = tail.substring(0, idx);
                            if (!before.isEmpty()) contentBuf.append(before);
                            DsmlParser.parse(tail.substring(idx), tcName, tcArgs);
                        } else {
                            contentBuf.append(tail);
                        }
                    }
                    // 兜底：从完整 content 搜未处理的 DSML
                    if ((tcName[0] == null || tcName[0].isEmpty())) {
                        String full = contentBuf.toString();
                        int idx = full.indexOf("<DSML");
                        if (idx >= 0) {
                            DsmlParser.parse(full.substring(idx), tcName, tcArgs);
                            contentBuf.setLength(idx);
                        }
                    }

                    // After first stream ends: check if tool was called
                    if ((tcName[0] == null || tcName[0].isEmpty())) {
                        // No tool call — normal path, persist and done
                        String content = contentBuf.toString();
                        if (thinking && thinkingBuf != null) {
                            String full = thinkingBuf.isEmpty() ? content
                                    : "<thinking>" + thinkingBuf.toString() + "</thinking>\n" + content;
                            saveMessage(convoId, "assistant", full, null);
                            chatMemory.add(memoryKey, List.of(new AssistantMessage(content)));
                        } else {
                            persistAssistant(convoId, memoryKey, content);
                        }
                        return Flux.<String>empty();
                    }

                    // === Tool call path ===
                    return handleToolCallAndContinue(tcName[0], tcArgs.toString(),
                            history, userMsg, convoId, memoryKey, userId);
                }))
                .doOnError(e -> {
                    log.warn("流式中断: conversationId={}", convoId);
                    if (contentBuf.length() > 0) {
                        persistAssistant(convoId, memoryKey, contentBuf.toString());
                    }
                })
                .onErrorResume(e ->
                        Flux.just("{\"type\":\"content\",\"content\":\"\\n\\n（AI 回复因网络原因中断，请重试）\"}"));
    }

    /**
     * 工具调用后的处理：
     * 1. 发送 tool_call 事件给前端
     * 2. 执行工具
     * 3. 构建新消息列表注入工具结果
     * 4. 循环调用 LLM（最多 MAX_TOOL_ROUNDS 轮）
     * 5. 最终回答流式输出给前端
     */
    private Flux<String> handleToolCallAndContinue(String toolName, String toolArgs,
                                                     List<Message> history, String userMsg,
                                                     Long convoId, String memoryKey,
                                                     Long userId) {
        log.info("工具调用: name={}, args={}", toolName, toolArgs);

        // 1. 通知前端有工具调用
        Flux<String> toolEvent;
        try {
            toolEvent = Flux.just("{\"type\":\"tool_call\",\"name\":\""
                    + toolName + "\",\"args\":" + CLEAN_MAPPER.writeValueAsString(toolArgs) + "}");
        } catch (Exception e) {
            toolEvent = Flux.just("{\"type\":\"tool_call\",\"name\":\"" + toolName + "\"}");
        }

        // 2-4. 工具执行循环 → 最终 LLM 调用
        Flux<String> answerFlux = Flux.defer(() -> {
            try {
                // 构建增强消息列表（history + user + tool results）
                List<Map<String, Object>> messages = buildMessagesWithTools(
                        history, userMsg, toolName, toolArgs, userId, convoId);

                // 工具循环调用（同步，最多 MAX_TOOL_ROUNDS 轮）
                String finalAnswer = callLlmWithTools(messages, 0);

                // 持久化
                persistAssistant(convoId, memoryKey, finalAnswer);

                // 流式输出最终回答
                return Flux.just("{\"type\":\"content\",\"content\":"
                        + CLEAN_MAPPER.writeValueAsString(finalAnswer) + "}");

            } catch (Exception e) {
                log.error("工具调用 loop 失败", e);
                return Flux.just("{\"type\":\"content\",\"content\":\"\\n（工具执行失败: "
                        + e.getMessage() + "）\"}");
            }
        });

        return toolEvent.concatWith(answerFlux);
    }

    /**
     * 构建包含工具调用结果的消息列表（Map 格式，用于二次请求）。
     */
    private List<Map<String, Object>> buildMessagesWithTools(List<Message> history,
                                                               String userMsg,
                                                               String toolName, String toolArgs,
                                                               Long userId, Long convoId) {
        ToolContext.setUser(userId);
        ToolContext.setConversation(convoId);
        List<Map<String, Object>> messages = new ArrayList<>();
        String systemPrompt = getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        if (history != null) {
            for (Message msg : history) {
                messages.add(Map.of("role",
                        msg.getMessageType().name().toLowerCase(), "content", msg.getText()));
            }
        }
        messages.add(Map.of("role", "user", "content", userMsg != null ? userMsg : ""));

        // Assistant 的工具调用请求
        messages.add(Map.of("role", "assistant",
                "tool_calls", List.of(Map.of(
                        "id", "call_1",
                        "type", "function",
                        "function", Map.of("name", toolName, "arguments", toolArgs)))));

        // 执行工具
        String result = toolRegistry.execute(toolName, toolArgs);
        log.info("工具执行结果: name={}, result={}", toolName,
                result.length() > 100 ? result.substring(0, 100) + "..." : result);

        // Tool 结果消息
        messages.add(Map.of("role", "tool",
                "tool_call_id", "call_1",
                "content", result));

        return messages;
    }

    /**
     * 同步调用 LLM（支持工具循环，最多 maxRounds 轮）。
     */
    private String callLlmWithTools(List<Map<String, Object>> messages, int round) {
        if (round >= MAX_TOOL_ROUNDS) {
            log.warn("工具调用超过最大轮次 {}，强制终止", MAX_TOOL_ROUNDS);
            return "（工具调用次数过多，已终止）";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", getModel());
        body.put("messages", messages);
        body.put("stream", false);

        try {
            String json = CLEAN_MAPPER.writeValueAsString(body);
            String resp = apiClient.sync(json);
            JsonNode root = CLEAN_MAPPER.readTree(resp);
            JsonNode msg = root.path("choices").get(0).path("message");

            // Check if LLM wants to call another tool
            if (msg.has("tool_calls") && !msg.get("tool_calls").isNull()) {
                JsonNode tcArray = msg.get("tool_calls");
                for (JsonNode tc : tcArray) {
                    JsonNode func = tc.path("function");
                    String name = func.path("name").asText();
                    String args = func.path("arguments").asText();

                    // Execute
                    String result = toolRegistry.execute(name, args);

                    // Add assistant tool_call message + tool result
                    messages.add(Map.of("role", "assistant",
                            "tool_calls", List.of(Map.of(
                                    "id", "call_" + (round + 1),
                                    "type", "function",
                                    "function", Map.of("name", name, "arguments", args)))));
                    messages.add(Map.of("role", "tool",
                            "tool_call_id", "call_" + (round + 1),
                            "content", result));
                }
                // 递归处理后续工具调用
                return callLlmWithTools(messages, round + 1);
            }

            // 正常文本回答
            return msg.path("content").asText("");

        } catch (Exception e) {
            log.error("工具调用 LLM 请求失败", e);
            return "（工具调用后 AI 回答生成失败: " + e.getMessage() + "）";
        }
    }

    /**
     * 同步工具循环（给同步 chat() 方法用）。
     */
    private String runToolLoop(List<Message> history, String userMsg,
                                JsonNode toolCalls, Long userId, Long convoId) {
        ToolContext.setUser(userId);
        ToolContext.setConversation(convoId);
        try {
            // 提取第一个 tool_call
            JsonNode tc = toolCalls.get(0);
            String name = tc.path("function").path("name").asText();
            String args = tc.path("function").path("arguments").asText();

            List<Map<String, Object>> messages = buildMessagesWithTools(
                    history, userMsg, name, args, userId, convoId);
            return callLlmWithTools(messages, 0);
        } finally {
            ToolContext.clear();
        }
    }

    // ==================== 请求体 & SSE 解析 ====================

    /**
     * 构建 OpenAI 兼容 JSON 请求体。
     */
    private String buildRequestBody(List<Message> history, String userMsg, boolean thinking) {
        String ragContext = retrieveRelevantContext(userMsg);

        List<Map<String, Object>> messages = new ArrayList<>();
        String systemPrompt = getSystemPrompt();
        if (ragContext != null) {
            systemPrompt = systemPrompt + "\n\n## 知识库参考资料\n" + ragContext
                    + "\n请根据以上参考资料优先回答用户问题。如果资料不足以回答，请如实告知并结合你的知识给出补充。";
        }
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
        // 注入工具列表
        if (toolRegistry.hasTools()) {
            body.put("tools", toolRegistry.getToolsJson());
        }
        try {
            String json = CLEAN_MAPPER.writeValueAsString(body);
            log.info("API请求: model={}, thinking={}, tools={}, msgs={}, bodyLen={}",
                    model, thinking, toolRegistry.count(), messages.size(), json.length());
            return json;
        } catch (Exception e) {
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    /**
     * 微批量 flush：扫描缓冲区中的 DSML 标签，只把安全的文本 emit 到前端。
     * 标签残留跨越批次时，通过 tagPrefix 和 dsmlPending 在批次间传递。
     */
    private void flushBatch(StringBuilder batch, StringBuilder persistent,
                            reactor.core.publisher.SynchronousSink<String> sink,
                            String[] tcName, StringBuilder tcArgs,
                            String[] tagPrefix, String[] dsmlPending) {
        String text = dsmlPending[0] + tagPrefix[0] + batch.toString();
        tagPrefix[0] = "";
        dsmlPending[0] = "";
        batch.setLength(0);

        // 1. 搜 DSML 起始标记（<DSML| 或 <DSML｜）
        int dsmlStart = DsmlParser.indexOf(text);
        if (dsmlStart >= 0) {
            String before = text.substring(0, dsmlStart);
            if (!before.isEmpty()) {
                persistent.append(before);
                try { sink.next("{\"type\":\"content\",\"content\":"
                        + CLEAN_MAPPER.writeValueAsString(before) + "}"); } catch (Exception ignored) {}
            }
            String dsml = text.substring(dsmlStart);
            String nameBefore = tcName[0];
            DsmlParser.parse(dsml, tcName, tcArgs);
            // 解析失败（DSML 块不完整，跨批次了）→ 暂存，下批再试
            if (tcName[0] == null || tcName[0].isEmpty()) {
                dsmlPending[0] = dsml;
                tcName[0] = nameBefore; // 恢复旧值
                return;
            }
            return;
        }

        // 2. 没找到 DSML → 检查末尾是否有标签前缀残留
        tagPrefix[0] = DsmlParser.trailingPrefix(text);
        if (!tagPrefix[0].isEmpty()) {
            text = text.substring(0, text.length() - tagPrefix[0].length());
        }

        // 3. emit 安全文本
        if (!text.isEmpty()) {
            persistent.append(text);
            try { sink.next("{\"type\":\"content\",\"content\":"
                    + CLEAN_MAPPER.writeValueAsString(text) + "}"); } catch (Exception ignored) {}
        }
    }

    private String retrieveRelevantContext(String query) {
        try {
            var results = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(query)
                            .topK(3)
                            .similarityThreshold(0.6)
                            .build());
            if (results.isEmpty()) return null;

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                var doc = results.get(i);
                String filename = doc.getMetadata().getOrDefault("filename", "未知").toString();
                sb.append("【来源: ").append(filename).append("】\n")
                        .append(doc.getText()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("RAG 检索失败（对话不受影响）: {}", e.getMessage());
            return null;
        }
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

    private void updateConversationMeta(Long convoId) {
        Conversation convo = conversationMapper.selectById(convoId);
        if (convo != null) {
            convo.setMessageCount((convo.getMessageCount() != null ? convo.getMessageCount() : 0) + 2);
            conversationMapper.updateById(convo);
        }
    }
}
