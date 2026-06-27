/*
 * Copyright (c) 2026 Jiang.
 * https://github.com/<your-username>/jiang-I-agent
 *
 * 本项目为个人开源学习项目，禁止用于毕设 / 面试作品集等违规用途。
 * 详见项目根目录 LICENSE 文件。
 */
package com.jiang.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiang.entity.AgentConfig;
import com.jiang.entity.Conversation;
import com.jiang.mapper.AgentConfigMapper;
import com.jiang.mapper.ConversationMapper;
import com.jiang.mapper.MessageMapper;
import com.jiang.model.req.ChatRequest;
import com.jiang.model.resp.ChatResponse;
import com.jiang.tool.ToolContext;
import com.jiang.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionChunk;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话服务 — Agent 核心调度。
 * <p>
 * 请求构建、SSE 解析、工具调用、消息持久化全部在此。
 * 响应解析使用 Spring AI DeepSeek 专用类型（{@link ChatCompletionChunk} / {@link ChatCompletion}），
 * 自带 reasoningContent 字段，替代了之前的手动 JsonNode 遍历。
 */
@Slf4j
@Service
@org.springframework.transaction.annotation.Transactional
public class ChatService {

    private final ChatMemory chatMemory;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AgentConfigMapper agentConfigMapper;
    private final VectorStore vectorStore;
    private final ToolRegistry toolRegistry;
    private final String defaultSystemPrompt;

    @Value("${spring.ai.openai.chat.model}")
    private String defaultModel;

    private static final int MAX_TOOL_ROUNDS = 10;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private static final Duration STREAM_REQUEST_TIMEOUT = Duration.ofMinutes(3);

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    public ChatService(ChatMemory chatMemory, ConversationMapper conversationMapper,
                       MessageMapper messageMapper, AgentConfigMapper agentConfigMapper,
                       VectorStore vectorStore, ToolRegistry toolRegistry,
                       @Qualifier("defaultSystemPrompt") String defaultSystemPrompt) {
        this.chatMemory = chatMemory;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.agentConfigMapper = agentConfigMapper;
        this.vectorStore = vectorStore;
        this.toolRegistry = toolRegistry;
        this.defaultSystemPrompt = defaultSystemPrompt;
    }

    // ==================== 提示词 / 模型读取 ====================

    private String getDbSystemPrompt() {
        try {
            AgentConfig config = agentConfigMapper.selectById(1);
            if (config != null && config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
                return config.getSystemPrompt();
            }
        } catch (Exception e) { log.debug("DB AgentConfig 读取失败", e); }
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

    public Flux<String> streamChat(ChatRequest request, Long userId) {
        var ctx = prepareConversation(request, userId);
        ToolContext.setUser(userId);
        ToolContext.setConversation(ctx.convoId);
        return streamWithPossibleTools(ctx.history, request.getMessage(),
                ctx.convoId, ctx.memoryKey, false, userId)
                .doFinally(s -> ToolContext.clear());
    }

    public Flux<String> streamChatWithThinking(ChatRequest request, Long userId) {
        var ctx = prepareConversation(request, userId);
        ToolContext.setUser(userId);
        ToolContext.setConversation(ctx.convoId);
        return streamWithPossibleTools(ctx.history, request.getMessage(),
                ctx.convoId, ctx.memoryKey, true, userId)
                .doFinally(s -> ToolContext.clear());
    }

    // ==================== 会话准备 & 持久化 ====================

    private ConversationContext prepareConversation(ChatRequest request, Long userId) {
        Long convoId = resolveConversationId(request.getConversationId(), request.getMessage(), userId);
        String memoryKey = String.valueOf(convoId);
        saveMessage(convoId, "user", request.getMessage(), null, null);
        var history = chatMemory.get(memoryKey);
        chatMemory.add(memoryKey, List.of(new UserMessage(request.getMessage())));
        return new ConversationContext(convoId, memoryKey, history);
    }

    private void saveAssistantMessage(ConversationContext ctx, String content) {
        saveMessage(ctx.convoId, "assistant", content, null, null);
        chatMemory.add(ctx.memoryKey, List.of(new AssistantMessage(content)));
        updateConversationMeta(ctx.convoId);
    }

    private record ConversationContext(Long convoId, String memoryKey, List<Message> history) {}

    private void persistAssistant(Long convoId, String memoryKey, String content, String thinking) {
        saveMessage(convoId, "assistant", content, thinking, null);
        if (thinking != null && !thinking.isEmpty()) {
            chatMemory.add(memoryKey, List.of(new AssistantMessage("<thinking>" + thinking + "</thinking>\n" + content)));
        } else {
            chatMemory.add(memoryKey, List.of(new AssistantMessage(content)));
        }
        updateConversationMeta(convoId);
    }

    // ==================== LLM 调用（同步） ====================

    private String doCall(List<Message> history, String userMsg, Long userId, Long convoId) {
        String body = buildRequestBody(history, userMsg, false, false);
        String resp = syncHttp(body);
        try {
            var completion = MAPPER.readValue(resp, ChatCompletion.class);
            var msg = completion.choices().get(0).message();

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                return runToolLoop(history, userMsg, msg, userId, convoId);
            }
            return stripDsml(msg.content());
        } catch (Exception e) {
            throw new RuntimeException("解析同步响应失败", e);
        }
    }

    // ==================== LLM 调用（流式） ====================

    private Flux<String> streamWithPossibleTools(List<Message> history, String userMsg,
                                                   Long convoId, String memoryKey,
                                                   boolean thinking, Long userId) {
        // 按 tool_call index 分组累积，支持并行工具调用（避免参数粘在一起）
        final Map<Integer, String> tcNames = new LinkedHashMap<>();
        final Map<Integer, StringBuilder> tcArgsMap = new LinkedHashMap<>();
        final StringBuilder contentBuf = new StringBuilder();
        final StringBuilder thinkingBuf = thinking ? new StringBuilder() : null;
        final StringBuilder dsmlBuf = new StringBuilder();  // 记录被过滤的 DSML 内容
        String body = buildRequestBody(history, userMsg, thinking, true);

        return streamHttp(body)
                .<String>handle((chunk, sink) -> {
                    try {
                        var delta = chunk.choices().get(0).delta();

                        // tool_calls — 按 index 独立累积每个工具的名称和参数
                        if (delta.toolCalls() != null) {
                            for (var tc : delta.toolCalls()) {
                                int idx = tc.index() != null ? tc.index() : 0;
                                String tcName = tc.function().name();
                                String tcArg = tc.function().arguments();
                                if (tcName != null && !tcName.isEmpty()) {
                                    log.info("[TOOL_DELTA] index={} name={} args={}", idx, tcName,
                                            tcArg != null ? tcArg : "(null)");
                                    tcNames.put(idx, tcName);
                                }
                                if (tcArg != null && !tcArg.isEmpty()) {
                                    tcArgsMap.computeIfAbsent(idx, k -> new StringBuilder()).append(tcArg);
                                }
                            }
                            if (!tcNames.isEmpty()) return;
                        }

                        // reasoning_content
                        if (thinking) {
                            String rc = delta.reasoningContent();
                            if (rc != null && !rc.isEmpty()) {
                                thinkingBuf.append(rc);
                                sink.next("{\"type\":\"thinking\",\"content\":"
                                        + MAPPER.writeValueAsString(rc) + "}");
                            }
                        }

                        // content
                        String c = delta.content();
                        if (c == null || c.isEmpty()) return;
                        // DSML 过滤 — 加日志记录被拦截的内容
                        if (c.contains("<tool_calls>") || c.contains("<invoke ") || c.contains("</tool_calls>")) {
                            dsmlBuf.append(c);
                            log.info("[DSML_FILTER] 拦截 DSML 片段: {}", c.length() > 200 ? c.substring(0,200)+"..." : c);
                            return;
                        }
                        // 实时发射 content（支持打字机效果），同时缓冲用于持久化
                        contentBuf.append(c);
                        try {
                            sink.next("{\"type\":\"content\",\"content\":"
                                    + MAPPER.writeValueAsString(c) + "}");
                        } catch (Exception ignored) {}

                    } catch (Exception ignored) {}
                })
                .concatWith(Flux.defer(() -> {
                    if (tcNames.isEmpty()) {
                        // 终轮：正文已实时发射，只需持久化
                        if (dsmlBuf.length() > 0) {
                            log.info("[DSML_SUMMARY] 本轮共拦截 DSML {} 字符，对话正常结束。"
                                    + " DSML 前300字: {}", dsmlBuf.length(),
                                    dsmlBuf.substring(0, Math.min(300, dsmlBuf.length())));
                        }
                        String content = contentBuf.toString();
                        String thinkingStr = (thinking && thinkingBuf != null && !thinkingBuf.isEmpty())
                                ? thinkingBuf.toString() : null;
                        saveMessage(convoId, "assistant", content, thinkingStr, null);
                        if (thinkingStr != null) {
                            chatMemory.add(memoryKey, List.of(new AssistantMessage(
                                    "<thinking>" + thinkingStr + "</thinking>\n" + content)));
                        } else {
                            chatMemory.add(memoryKey, List.of(new AssistantMessage(content)));
                        }
                        updateConversationMeta(convoId);
                        return Flux.empty();
                    }

                    // 中间轮：发送 clear 事件让前端清掉中间轮次的内容
                    List<ToolCall> calls = new ArrayList<>();
                    for (var idx : tcNames.keySet().stream().sorted().toList()) {
                        String name = tcNames.get(idx);
                        String args = tcArgsMap.getOrDefault(idx, new StringBuilder()).toString();
                        if (name != null && !name.isEmpty()) calls.add(new ToolCall(name, args));
                    }
                    log.info("[TOOL_CALLS] 流式累积完成: {} 个工具调用, 中间内容 {} 字符（不发射）",
                            calls.size(), contentBuf.length());
                    for (var tc : calls) {
                        log.info("[TOOL_CALL] name={} args={}", tc.name(), tc.arguments());
                    }
                    ToolContext.setReasoning(thinkingBuf != null ? thinkingBuf.toString() : null);
                    return handleToolCallAndContinue(calls,
                            history, userMsg, convoId, memoryKey, userId);
                }))
                .doOnError(e -> {
                    log.warn("流式中断: conversationId={}", convoId);
                    if (contentBuf.length() > 0) {
                        persistAssistant(convoId, memoryKey, contentBuf.toString(),
                                thinkingBuf != null ? thinkingBuf.toString() : null);
                    }
                })
                .onErrorResume(e ->
                        Flux.just("{\"type\":\"content\",\"content\":\"\\n\\n（AI 回复因网络原因中断，请重试）\"}"));
    }

    // ==================== 工具调用处理 ====================

    /**
     * 单次工具调用（流式累积的结果）。
     */
    private record ToolCall(String name, String arguments) {}

    /**
     * 处理流式累积的工具调用列表（支持并行调用）。
     * 每个工具独立执行，结果合并为一个 assistant tool_calls 消息发送给 LLM。
     */
    private Flux<String> handleToolCallAndContinue(List<ToolCall> toolCalls,
                                                     List<Message> history, String userMsg,
                                                     Long convoId, String memoryKey, Long userId) {
        for (var tc : toolCalls) {
            log.info("工具调用: name={}, args={}", tc.name(), tc.arguments());
        }

        // 发送 tool_call 事件（合并为数组）
        Flux<String> toolEvent;
        try {
            toolEvent = Flux.just("{\"type\":\"tool_call\",\"name\":\""
                    + toolCalls.get(0).name() + "\",\"args\":"
                    + MAPPER.writeValueAsString(toolCalls.size() == 1
                    ? toolCalls.get(0).arguments()
                    : toolCalls.stream().map(ToolCall::arguments).toList().toString()) + "}");
        } catch (Exception e) {
            toolEvent = Flux.just("{\"type\":\"tool_call\",\"name\":\"" + toolCalls.get(0).name() + "\"}");
        }

        // 工具执行 + 流式 follow-up（支持思考内容和递归工具调用）
        Flux<String> followupFlux = Flux.defer(() -> {
            try {
                List<Map<String, Object>> messages = buildMessagesWithTools(
                        history, userMsg, toolCalls, userId, convoId);
                boolean thinking = ToolContext.getReasoning() != null;
                return streamToolFollowup(messages, 0, thinking, convoId, memoryKey, userId);
            } catch (Exception e) {
                log.error("工具调用 loop 失败", e);
                return Flux.just("{\"type\":\"content\",\"content\":\"\\n（工具执行失败: "
                        + e.getMessage() + "）\"}");
            }
        });

        return toolEvent.concatWith(followupFlux);
    }

    /**
     * 流式工具 follow-up：用给定的消息列表做流式调用，支持思考 + 递归工具调用。
     * 这是 {@link #callLlmWithTools} 的流式替代。
     */
    private Flux<String> streamToolFollowup(List<Map<String, Object>> messages, int round,
                                             boolean thinking, Long convoId,
                                             String memoryKey, Long userId) {
        if (round >= MAX_TOOL_ROUNDS) {
            log.warn("工具调用超过最大轮次 {}，强制终止", MAX_TOOL_ROUNDS);
            return Flux.just("{\"type\":\"content\",\"content\":\"（工具调用次数过多，已终止）\"}");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", getModel());
        body.put("messages", messages);
        body.put("stream", true);
        if (toolRegistry.hasTools()) {
            body.put("tools", toolRegistry.getToolsJson());
        }

        String json;
        try { json = MAPPER.writeValueAsString(body); }
        catch (Exception e) { return Flux.error(e); }
        log.info("[TOOL_FOLLOWUP] round={} tools={} msgs={}", round, toolRegistry.count(), messages.size());

        final Map<Integer, String> tcNames = new LinkedHashMap<>();
        final Map<Integer, StringBuilder> tcArgsMap = new LinkedHashMap<>();
        final StringBuilder contentBuf = new StringBuilder();
        final StringBuilder thinkingBuf = new StringBuilder();

        return streamHttp(json)
                .<String>handle((chunk, sink) -> {
                    try {
                        var delta = chunk.choices().get(0).delta();

                        if (delta.toolCalls() != null) {
                            for (var tc : delta.toolCalls()) {
                                int idx = tc.index() != null ? tc.index() : 0;
                                String tcName = tc.function().name();
                                String tcArg = tc.function().arguments();
                                if (tcName != null && !tcName.isEmpty()) {
                                    log.info("[TOOL_DELTA] followup round={} index={} name={}", round, idx, tcName);
                                    tcNames.put(idx, tcName);
                                }
                                if (tcArg != null && !tcArg.isEmpty()) {
                                    tcArgsMap.computeIfAbsent(idx, k -> new StringBuilder()).append(tcArg);
                                }
                            }
                            if (!tcNames.isEmpty()) return;
                        }

                        String rc = delta.reasoningContent();
                        if (rc != null && !rc.isEmpty()) {
                            thinkingBuf.append(rc);
                            sink.next("{\"type\":\"thinking\",\"content\":"
                                    + MAPPER.writeValueAsString(rc) + "}");
                        }

                        String c = delta.content();
                        if (c == null || c.isEmpty()) return;
                        // DSML 过滤（follow-up 也加）
                        if (c.contains("<tool_calls>") || c.contains("<invoke ") || c.contains("</tool_calls>")) {
                            log.info("[DSML_FILTER] followup 拦截 DSML: {}...", c.substring(0, Math.min(100,c.length())));
                            return;
                        }
                        // 实时发射 content，同时缓冲用于持久化
                        contentBuf.append(c);
                        try {
                            sink.next("{\"type\":\"content\",\"content\":"
                                    + MAPPER.writeValueAsString(c) + "}");
                        } catch (Exception ignored) {}

                    } catch (Exception ignored) {}
                })
                .concatWith(Flux.defer(() -> {
                    if (!tcNames.isEmpty()) {
                        // 中间轮：递归执行工具（不清空缓冲——前端在 tool_call 事件时自动重置）
                        List<ToolCall> calls = new ArrayList<>();
                        for (var idx : tcNames.keySet().stream().sorted().toList()) {
                            String name = tcNames.get(idx);
                            String args = tcArgsMap.getOrDefault(idx, new StringBuilder()).toString();
                            if (name != null && !name.isEmpty()) calls.add(new ToolCall(name, args));
                        }
                        // 执行工具，追加结果到 messages，递归调用
                        for (var tc : calls) {
                            String result = toolRegistry.execute(tc.name(), tc.arguments());
                            log.info("[TOOL_RESULT] followup name={} result={}", tc.name(),
                                    result.length() > 200 ? result.substring(0, 200) + "..." : result);

                            Map<String, Object> asst = new LinkedHashMap<>();
                            asst.put("role", "assistant");
                            asst.put("tool_calls", List.of(Map.of(
                                    "id", "call_f_" + round, "type", "function",
                                    "function", Map.of("name", tc.name(), "arguments", tc.arguments()))));
                            String reasoning = thinkingBuf.toString();
                            if (!reasoning.isEmpty()) asst.put("reasoning_content", reasoning);
                            messages.add(asst);
                            messages.add(Map.of("role", "tool", "tool_call_id", "call_f_" + round, "content", result));
                        }
                        return streamToolFollowup(messages, round + 1, thinking, convoId, memoryKey, userId);
                    }

                    // 终轮：正文已实时发射，只需持久化
                    String content = contentBuf.toString();
                    String thinkingStr = !thinkingBuf.isEmpty() ? thinkingBuf.toString() : null;
                    saveMessage(convoId, "assistant", content, thinkingStr, null);
                    if (thinkingStr != null) {
                        chatMemory.add(memoryKey, List.of(new AssistantMessage(
                                "<thinking>" + thinkingStr + "</thinking>\n" + content)));
                    } else {
                        chatMemory.add(memoryKey, List.of(new AssistantMessage(content)));
                    }
                    return Flux.<String>empty();
                }));
    }

    private List<Map<String, Object>> buildMessagesWithTools(List<Message> history,
                                                               String userMsg,
                                                               List<ToolCall> toolCalls,
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

        // 构建 assistant 消息：所有并行 tool_calls 合并
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        List<Map<String, Object>> tcList = new ArrayList<>();
        for (int i = 0; i < toolCalls.size(); i++) {
            var tc = toolCalls.get(i);
            tcList.add(Map.of(
                    "id", "call_" + (i + 1), "type", "function",
                    "function", Map.of("name", tc.name(), "arguments", tc.arguments())));
        }
        assistantMsg.put("tool_calls", tcList);
        String rc = ToolContext.getReasoning();
        if (rc != null && !rc.isEmpty()) {
            log.info("[TOOL_MSG] assistant 消息含 reasoning_content: {} 字符", rc.length());
            assistantMsg.put("reasoning_content", rc);
        } else {
            log.info("[TOOL_MSG] assistant 消息无 reasoning_content");
        }
        messages.add(assistantMsg);

        // 执行每个工具，添加 tool 结果消息
        for (int i = 0; i < toolCalls.size(); i++) {
            var tc = toolCalls.get(i);
            log.info("[TOOL_EXEC] 执行工具: name={} args={}", tc.name(), tc.arguments());
            String result = toolRegistry.execute(tc.name(), tc.arguments());
            log.info("[TOOL_RESULT] name={} result={}", tc.name(),
                    result.length() > 200 ? result.substring(0, 200) + "..." : result);
            messages.add(Map.of("role", "tool",
                    "tool_call_id", "call_" + (i + 1), "content", result));
        }
        log.info("[TOOL_MSG] 构建完成: {} 个工具结果, 共 {} 条消息", toolCalls.size(), messages.size());
        return messages;
    }

    private String callLlmWithTools(List<Map<String, Object>> messages, int round) {
        if (round >= MAX_TOOL_ROUNDS) {
            log.warn("工具调用超过最大轮次 {}，强制终止", MAX_TOOL_ROUNDS);
            return "（工具调用次数过多，已终止）";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", getModel());
        body.put("messages", messages);
        body.put("stream", false);
        // 关键：follow-up 请求也必须带 tools，否则模型想调工具只能 fallback 到 DSML
        if (toolRegistry.hasTools()) {
            body.put("tools", toolRegistry.getToolsJson());
        }

        try {
            String json = MAPPER.writeValueAsString(body);
            log.info("[TOOL_FOLLOWUP] round={} tools={} msgs={}", round, toolRegistry.count(), messages.size());
            String resp = syncHttp(json);
            var completion = MAPPER.readValue(resp, ChatCompletion.class);
            var msg = completion.choices().get(0).message();

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                for (var tc : msg.toolCalls()) {
                    String name = tc.function().name();
                    String args = tc.function().arguments();
                    String result = toolRegistry.execute(name, args);

                    Map<String, Object> tcAssistant = new LinkedHashMap<>();
                    tcAssistant.put("role", "assistant");
                    tcAssistant.put("tool_calls", List.of(Map.of(
                            "id", "call_" + (round + 1), "type", "function",
                            "function", Map.of("name", name, "arguments", args))));
                    if (msg.reasoningContent() != null && !msg.reasoningContent().isEmpty()) {
                        tcAssistant.put("reasoning_content", msg.reasoningContent());
                    }
                    messages.add(tcAssistant);
                    messages.add(Map.of("role", "tool",
                            "tool_call_id", "call_" + (round + 1), "content", result));
                }
                return callLlmWithTools(messages, round + 1);
            }

            return stripDsml(msg.content());

        } catch (Exception e) {
            log.error("工具调用 LLM 请求失败", e);
            return "（工具调用后 AI 回答生成失败: " + e.getMessage() + "）";
        }
    }

    /** 同步路径 DSML 过滤：如果 content 包含 DSML 标签，截断返回正文部分 */
    private String stripDsml(String content) {
        if (content == null || content.isEmpty()) return "";
        if (content.contains("<tool_calls>") || content.contains("<invoke ")) {
            int start = content.indexOf("<tool_calls>");
            if (start < 0) start = content.indexOf("<invoke ");
            if (start > 0) {
                log.info("[DSML_SYNC] 同步路径拦截 DSML，截断前 {} 字符", start);
                return content.substring(0, start).trim();
            }
            log.info("[DSML_SYNC] 同步路径拦截 DSML（无正文部分）");
            return "";
        }
        return content;
    }

    private String runToolLoop(List<Message> history, String userMsg,
                                ChatCompletionMessage msg, Long userId, Long convoId) {
        ToolContext.setUser(userId);
        ToolContext.setConversation(convoId);
        try {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (var tc : msg.toolCalls()) {
                toolCalls.add(new ToolCall(tc.function().name(), tc.function().arguments()));
            }

            // 同步路径也把 reasoning_content 存入 ToolContext（如有）
            if (msg.reasoningContent() != null && !msg.reasoningContent().isEmpty()) {
                ToolContext.setReasoning(msg.reasoningContent());
            }

            List<Map<String, Object>> messages = buildMessagesWithTools(
                    history, userMsg, toolCalls, userId, convoId);
            return callLlmWithTools(messages, 0);
        } finally {
            ToolContext.clear();
        }
    }

    // ==================== HTTP 传输（替代 DeepSeekStreamService） ====================

    private String syncHttp(String body) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.error("API 返回 {}: {}", resp.statusCode(), resp.body());
                throw new RuntimeException("API " + resp.statusCode());
            }
            return resp.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("同步请求失败", e);
            throw new RuntimeException("AI 调用失败: " + e.getMessage());
        }
    }

    /** 流式 HTTP，返回 Flux<ChatCompletionChunk>（用 Spring AI 类型解析，自带 reasoningContent） */
    private Flux<ChatCompletionChunk> streamHttp(String body) {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(STREAM_REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return Mono.fromFuture(httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofLines()))
                .flatMapMany(resp -> {
                    if (resp.statusCode() != 200) {
                        String err = resp.body().findFirst().orElse("(empty body)");
                        log.error("API 返回 {}: {}", resp.statusCode(), err);
                        return Flux.error(new RuntimeException("API " + resp.statusCode() + ": " + err));
                    }
                    return Flux.fromStream(resp.body()
                                    .filter(line -> line.startsWith("data: "))
                                    .map(line -> line.substring(6)))
                            .takeWhile(data -> !"[DONE]".equals(data))
                            .map(data -> {
                                try { return MAPPER.readValue(data, ChatCompletionChunk.class); }
                                catch (Exception e) { throw new RuntimeException(e); }
                            });
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== 请求体构建 ====================

    private String buildRequestBody(List<Message> history, String userMsg, boolean thinking,
                                     boolean stream) {
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
        body.put("stream", stream);
        if (thinking && (model == null || !model.contains("R1"))) {
            body.put("thinking", Map.of("type", "enabled"));
        }
        if (toolRegistry.hasTools()) {
            body.put("tools", toolRegistry.getToolsJson());
        }
        try {
            String json = MAPPER.writeValueAsString(body);
            log.info("API请求: model={}, thinking={}, stream={}, tools={}, msgs={}, bodyLen={}",
                    model, thinking, stream, toolRegistry.count(), messages.size(), json.length());
            return json;
        } catch (Exception e) {
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    private String retrieveRelevantContext(String query) {
        try {
            var results = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(query).topK(3).similarityThreshold(0.6).build());
            if (results.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (var doc : results) {
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
                log.warn("越权访问会话: userId={}, conversationId={}, owner={}", userId, id, existing.getUserId());
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

    private void saveMessage(Long conversationId, String role, String content, String thinking, String toolCalls) {
        com.jiang.entity.Message msg = new com.jiang.entity.Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setThinking(thinking);
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
