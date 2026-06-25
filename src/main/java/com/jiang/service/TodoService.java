package com.jiang.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiang.entity.TodoItem;
import com.jiang.mapper.TodoItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办事项 CRUD 服务（供 Controller 使用）。
 * Agent 工具入口在 {@code com.jiang.tool.TodoTool}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoItemMapper todoItemMapper;

    // ==================== CRUD ====================

    public TodoItem create(Long userId, String title, String dueDate) {
        TodoItem item = new TodoItem();
        item.setUserId(userId);
        item.setTitle(title);
        item.setIsDone(0);
        if (dueDate != null && !dueDate.isEmpty()) {
            item.setDueDate(LocalDate.parse(dueDate));
        }
        todoItemMapper.insert(item);
        log.info("待办已创建: id={}, title={}, userId={}", item.getId(), title, userId);
        return item;
    }

    public List<TodoItem> listByUser(Long userId, Boolean done) {
        LambdaQueryWrapper<TodoItem> qw = new LambdaQueryWrapper<TodoItem>()
                .eq(TodoItem::getUserId, userId);
        if (done != null) qw.eq(TodoItem::getIsDone, done ? 1 : 0);
        qw.orderByDesc(TodoItem::getCreatedAt);
        return todoItemMapper.selectList(qw);
    }

    public TodoItem getById(Long id) {
        return todoItemMapper.selectById(id);
    }

    public void complete(Long userId, Long id) {
        TodoItem item = todoItemMapper.selectById(id);
        if (item == null || !item.getUserId().equals(userId)) return;
        item.setIsDone(1);
        item.setCompletedAt(LocalDateTime.now());
        todoItemMapper.updateById(item);
        log.info("待办已完成: id={}, title={}", id, item.getTitle());
    }

    public void delete(Long userId, Long id) {
        TodoItem item = todoItemMapper.selectById(id);
        if (item == null || !item.getUserId().equals(userId)) return;
        todoItemMapper.deleteById(id);
        log.info("待办已删除: id={}", id);
    }
}
