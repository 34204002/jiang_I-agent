package com.jiang.tool;

import com.jiang.entity.TodoItem;
import com.jiang.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 待办工具集 — Agent 可调用的待办操作。
 * <p>
 * 仅负责 LLM 交互面（参数校验、格式化输出），实际数据操作委托给 {@link TodoService}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoTool {

    private final TodoService todoService;

    @Tool(name = "create_todo",
          description = "创建一个新的待办事项。当用户说「记一下」「帮我记」「提醒我」「添加待办」时使用。",
          parameters = """
              {
                  "type": "object",
                  "properties": {
                      "title": {"type": "string", "description": "待办标题（必填）"},
                      "dueDate": {"type": "string", "description": "截止日期，格式 yyyy-MM-dd，可省略"}
                  },
                  "required": ["title"]
              }
              """)
    public String createTodo(String title, String dueDate) {
        if (dueDate != null && !dueDate.isEmpty()) {
            try {
                LocalDate.parse(dueDate, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                return "日期格式错误: " + dueDate + "，请使用 yyyy-MM-dd 格式";
            }
        }
        TodoItem item = todoService.create(ToolContext.getUser(), title, dueDate);
        return "待办已创建: #" + item.getId() + " — " + title
                + (dueDate != null ? " (截止: " + dueDate + ")" : "");
    }

    @Tool(name = "list_todos",
          description = "查询当前用户的待办列表。当用户问「有哪些待办」「我的待办」「查看待办」时使用。",
          parameters = """
              {
                  "type": "object",
                  "properties": {
                      "status": {"type": "string", "description": "筛选状态: pending(未完成) / done(已完成) / all(全部)，默认 pending"}
                  },
                  "required": []
              }
              """)
    public String listTodos(String status) {
        Long userId = ToolContext.getUser();
        Boolean done = null;
        if ("done".equals(status)) done = true;
        else if ("pending".equals(status) || status == null || status.isEmpty()) done = false;

        List<TodoItem> items = todoService.listByUser(userId, done);

        if (items.isEmpty()) {
            return "你当前没有" + ("done".equals(status) ? "已完成" :
                    "all".equals(status) ? "任何" : "未完成") + "的待办事项。";
        }

        return items.stream()
                .map(item -> {
                    String prefix = item.getIsDone() == 1 ? "✅" : "📋";
                    String due = item.getDueDate() != null ? " 截止: " + item.getDueDate() : "";
                    return prefix + " #" + item.getId() + " " + item.getTitle() + due;
                })
                .collect(Collectors.joining("\n"));
    }

    @Tool(name = "complete_todo",
          description = "将待办标记为已完成。当用户说「做完了」「完成了」「搞定」「done」并指定待办时使用。",
          parameters = """
              {
                  "type": "object",
                  "properties": {
                      "todoId": {"type": "integer", "description": "待办 ID（必填）"}
                  },
                  "required": ["todoId"]
              }
              """)
    public String completeTodo(Long todoId) {
        TodoItem item = todoService.getById(todoId);
        if (item == null) return "未找到待办 #" + todoId;
        if (!item.getUserId().equals(ToolContext.getUser())) return "无权操作此待办";

        todoService.complete(ToolContext.getUser(), todoId);
        return "已标记完成: " + item.getTitle();
    }

    @Tool(name = "delete_todo",
          description = "删除一个待办事项。当用户说「删除待办」「取消待办」「不用记了」并指定待办时使用。",
          parameters = """
              {
                  "type": "object",
                  "properties": {
                      "todoId": {"type": "integer", "description": "待办 ID（必填）"}
                  },
                  "required": ["todoId"]
              }
              """)
    public String deleteTodo(Long todoId) {
        TodoItem item = todoService.getById(todoId);
        if (item == null) return "未找到待办 #" + todoId;
        if (!item.getUserId().equals(ToolContext.getUser())) return "无权操作此待办";

        String title = item.getTitle();
        todoService.delete(ToolContext.getUser(), todoId);
        return "已删除待办: " + title;
    }
}
