package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.entity.TodoItem;
import com.jiang.service.TodoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 待办事项 CRUD 接口。
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    /** 获取当前用户的待办列表 */
    @GetMapping
    public Result<List<TodoItem>> list(@RequestParam(defaultValue = "false") boolean done,
                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(todoService.listByUser(userId, done));
    }

    /** 创建待办 */
    @PostMapping
    public Result<TodoItem> create(@RequestBody Map<String, String> body,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        TodoItem item = todoService.create(userId,
                body.get("title"), body.get("dueDate"));
        return Result.success(item);
    }

    /** 完成待办 */
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id,
                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        todoService.complete(userId, id);
        return Result.success();
    }

    /** 删除待办 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        todoService.delete(userId, id);
        return Result.success();
    }
}
