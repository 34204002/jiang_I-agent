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

    /** 待办主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联会话 ID */
    private Long conversationId;

    /** 待办标题 */
    private String title;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 0-未完成 1-已完成 */
    private Integer isDone;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
