package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.model.req.ChatRequest;
import com.jiang.model.resp.ChatResponse;
import com.jiang.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 对话接口 — Phase 1
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 同步对话
     */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        ChatResponse resp = chatService.chat(request.getMessage(), request.getConversationId());
        return Result.success(resp);
    }

    /**
     * SSE 流式对话 — Spring MVC 自动将 Flux<String> 转为 text/event-stream
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String message,
                                    @RequestParam(required = false) String conversationId) {
        return chatService.streamChat(message, conversationId);
    }
}
