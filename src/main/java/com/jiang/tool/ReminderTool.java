package com.jiang.tool;

import com.jiang.entity.Reminder;
import com.jiang.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时提醒工具 — Agent 可调用的提醒操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderTool {

    private final ReminderService reminderService;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Tool(name = "create_reminder",
          description = "创建一个定时提醒，在指定时间通知用户。当用户说「提醒我」「半小时后提醒」「X点提醒我」时使用。",
          parameters = """
              {
                  "type": "object",
                  "properties": {
                      "message": {"type": "string", "description": "提醒内容"},
                      "remindTime": {"type": "string", "description": "提醒时间 yyyy-MM-dd HH:mm 格式"}
                  },
                  "required": ["message", "remindTime"]
              }
              """)
    public String createReminder(String message, String remindTime) {
        try {
            LocalDateTime remindAt = LocalDateTime.parse(remindTime, FMT);
            if (remindAt.isBefore(LocalDateTime.now())) {
                return "提醒时间不能是过去: " + remindTime + "，请重新指定一个未来时间";
            }
            Reminder r = reminderService.create(ToolContext.getUser(), message, remindAt);
            return "已设置提醒: " + remindTime + " — " + message + " (ID: #" + r.getId() + ")";
        } catch (DateTimeParseException e) {
            return "时间格式错误: " + remindTime + "，请使用 yyyy-MM-dd HH:mm 格式（如 2026-06-25 15:00）";
        }
    }

    @Tool(name = "list_reminders",
          description = "查看当前用户的提醒列表。当用户问「有哪些提醒」「我的提醒」时使用。",
          parameters = """
              {
                  "type": "object",
                  "properties": {
                      "status": {"type": "string", "description": "pending(未触发)/all(全部)，默认 pending"}
                  }
              }
              """)
    public String listReminders(String status) {
        boolean pendingOnly = !"all".equals(status);
        List<Reminder> items = reminderService.listByUser(ToolContext.getUser(), pendingOnly);

        if (items.isEmpty()) {
            return "你当前没有" + (pendingOnly ? "未触发的" : "") + "提醒。";
        }

        return items.stream()
                .map(r -> {
                    String icon = r.getFired() == 1 ? "✅" : "⏰";
                    return icon + " #" + r.getId() + " " + r.getMessage()
                            + " — " + r.getRemindAt().format(FMT);
                })
                .collect(Collectors.joining("\n"));
    }

    @Tool(name = "cancel_reminder",
          description = "取消一个未触发的提醒。当用户说「取消提醒」「不用提醒了」时使用。",
          parameters = """
              {
                  "type": "object",
                  "properties": {
                      "reminderId": {"type": "integer", "description": "提醒 ID"}
                  },
                  "required": ["reminderId"]
              }
              """)
    public String cancelReminder(Long reminderId) {
        reminderService.cancel(ToolContext.getUser(), reminderId);
        return "已取消提醒 #" + reminderId;
    }
}
