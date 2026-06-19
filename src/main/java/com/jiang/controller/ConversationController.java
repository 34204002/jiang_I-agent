package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.model.PageResult;
import com.jiang.model.vo.ConversationVO;
import com.jiang.model.vo.MessageVO;
import com.jiang.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 会话历史接口 — Phase 4
 */
@Slf4j
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** 会话列表 */
    @GetMapping
    public Result<PageResult<ConversationVO>> listConversations(@RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(conversationService.listConversations(userId, page, size));
    }

    /** 会话消息列表（校验归属） */
    @GetMapping("/{id}/messages")
    public Result<PageResult<MessageVO>> listMessages(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "50") int size,
                                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return Result.success(conversationService.listMessages(id, userId, page, size));
        } catch (SecurityException e) {
            return Result.fail(403, e.getMessage());
        }
    }

    /** 删除会话（校验归属，级联删除消息） */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            conversationService.deleteConversation(id, userId);
            return Result.success();
        } catch (SecurityException e) {
            return Result.fail(403, e.getMessage());
        }
    }
}
