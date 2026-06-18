package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.model.PageResult;
import com.jiang.model.vo.ConversationVO;
import com.jiang.model.vo.MessageVO;
import com.jiang.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 会话历史接口 — Phase 4
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** 会话列表 */
    @GetMapping
    public Result<PageResult<ConversationVO>> listConversations(@RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        PageResult<ConversationVO> result = conversationService.listConversations(page, size);
        return Result.success(result);
    }

    /** 会话消息列表 */
    @GetMapping("/{id}/messages")
    public Result<PageResult<MessageVO>> listMessages(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "50") int size) {
        PageResult<MessageVO> result = conversationService.listMessages(id, page, size);
        return Result.success(result);
    }

    /** 删除会话（级联删除消息） */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return Result.success();
    }
}
