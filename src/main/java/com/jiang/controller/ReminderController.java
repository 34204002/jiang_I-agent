package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.entity.Reminder;
import com.jiang.service.ReminderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时提醒接口 — 供前端轮询到期提醒并确认。
 */
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    /**
     * 当前用户的提醒列表。pending=true 只返回未触发的（前端轮询用）。
     */
    @GetMapping
    public Result<List<Reminder>> list(@RequestParam(defaultValue = "true") boolean pending,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(reminderService.listByUser(userId, pending));
    }

    /**
     * 确认提醒已送达（前端弹通知后调用，幂等）。
     */
    @PostMapping("/{id}/ack")
    public Result<Void> ack(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        reminderService.ack(userId, id);
        return Result.success();
    }
}
