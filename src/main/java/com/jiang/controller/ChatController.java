package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.model.req.ChatRequest;
import com.jiang.model.resp.ChatResponse;
import com.jiang.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 对话接口
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final String SSE_UTF8 = "text/event-stream;charset=UTF-8";

    private final ChatService chatService;

    /** 同步对话 */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request,
                                      HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        return Result.success(chatService.chat(request, userId));
    }

    /** SSE 流式对话 — GET（支持思考模式） */
    @GetMapping(value = "/stream", produces = SSE_UTF8)
    public Flux<String> streamChatGet(@RequestParam String message,
                                       @RequestParam(required = false) String conversationId,
                                       @RequestParam(required = false, defaultValue = "false") boolean thinking,
                                       HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        var request = new ChatRequest(message, conversationId);
        return thinking ? chatService.streamChatWithThinking(request, userId)
                        : chatService.streamChat(request, userId);
    }

    /** SSE 流式对话 — POST（支持思考模式） */
    @PostMapping(value = "/stream", produces = SSE_UTF8)
    public Flux<String> streamChatPost(@RequestBody ChatRequest request,
                                        @RequestParam(required = false, defaultValue = "false") boolean thinking,
                                        HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        return thinking ? chatService.streamChatWithThinking(request, userId)
                        : chatService.streamChat(request, userId);
    }

}
