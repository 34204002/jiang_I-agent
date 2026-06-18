package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.model.req.ChatRequest;
import com.jiang.model.resp.ChatResponse;
import com.jiang.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 对话接口 — Phase 1
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final String SSE_UTF8 = "text/event-stream;charset=UTF-8";

    private final ChatService chatService;

    /** 同步对话 */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        return Result.success(chatService.chat(request));
    }

    /** SSE 流式对话 — GET，供 EventSource 使用 */
    @GetMapping(value = "/stream", produces = SSE_UTF8)
    public Flux<String> streamChatGet(@RequestParam String message,
                                       @RequestParam(required = false) String conversationId) {
        return chatService.streamChat(new ChatRequest(message, conversationId));
    }

    /** SSE 流式对话 — POST 兼容 */
    @PostMapping(value = "/stream", produces = SSE_UTF8)
    public Flux<String> streamChatPost(@RequestBody ChatRequest request) {
        return chatService.streamChat(request);
    }
}
