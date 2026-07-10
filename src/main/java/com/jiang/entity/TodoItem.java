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

    /**
     * 所属用户
     */
    private Long userId;

    /**
     * 关联会话（可为空）
     */
    private Long conversationId;

    /**
     * 标题
     */
    private String title;

    /**
     * 截止日期
     */
    private LocalDate dueDate;

    /**
     * 是否完成：0-否 1-是
     */
    private Integer isDone;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
}
