package com.jiang.tool;

import com.jiang.entity.Document;
import com.jiang.entity.Reminder;
import com.jiang.entity.TodoItem;
import com.jiang.mapper.DocumentMapper;
import com.jiang.service.ReminderService;
import com.jiang.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 系统信息工具 — 让 Agent 查询数据库当前状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemTool {

    private final DocumentMapper documentMapper;
    private final TodoService todoService;
    private final ReminderService reminderService;

    @Tool(name = "list_knowledge",
          description = "查询知识库中已上传的文档列表。当用户问「有哪些文档」「知识库有什么」「已上传的文件」时使用。",
          parameters = "{}")
    public String listKnowledge() {
        var docs = documentMapper.selectList(null);
        if (docs.isEmpty()) return "知识库中还没有上传任何文档。";

        return docs.stream()
                .map(d -> {
                    String status = d.getStatus() == 2 ? "✅" : d.getStatus() == 1 ? "📋" : "⏳";
                    return status + " " + d.getFilename()
                            + " (" + (d.getFileSize() > 1048576
                                    ? (d.getFileSize() / 1048576) + "MB"
                                    : (d.getFileSize() / 1024) + "KB") + ")";
                })
                .collect(Collectors.joining("\n"));
    }

    @Tool(name = "get_status",
          description = "查询当前用户的状态概览。当用户问「我的状态」「有什么待办」「系统状态」时使用。"
                  + "返回待办数、提醒数、知识库文档数。",
          parameters = "{}")
    public String getStatus() {
        Long userId = ToolContext.getUser();
        var todos = todoService.listByUser(userId, false);  // pending only
        var reminders = reminderService.listByUser(userId, true); // pending
        var docs = documentMapper.selectList(null);
        long doneTodos = todoService.listByUser(userId, true).size();

        StringBuilder sb = new StringBuilder();
        sb.append("📋 未完成待办: ").append(todos.size()).append(" 个");
        if (doneTodos > 0) sb.append(" (已完成 ").append(doneTodos).append(" 个)");
        sb.append("\n⏰ 未触发提醒: ").append(reminders.size()).append(" 个");
        sb.append("\n📚 知识库文档: ").append(docs.size()).append(" 个");

        if (!todos.isEmpty()) {
            sb.append("\n\n待办列表:");
            for (TodoItem t : todos) {
                sb.append("\n  📋 ").append(t.getTitle());
                if (t.getDueDate() != null) sb.append(" (截止: ").append(t.getDueDate()).append(")");
            }
        }
        if (!reminders.isEmpty()) {
            sb.append("\n\n提醒列表:");
            for (Reminder r : reminders) {
                sb.append("\n  ⏰ ").append(r.getMessage())
                        .append(" — ").append(r.getRemindAt());
            }
        }
        return sb.toString();
    }
}
