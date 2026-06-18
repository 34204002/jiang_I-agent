package com.jiang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办事项实体 — t_todo_item
 */
@Data
@TableName("t_todo_item")
public class TodoItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private String title;
    private LocalDate dueDate;
    private Integer isDone;         // 0-未完成 1-已完成

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
