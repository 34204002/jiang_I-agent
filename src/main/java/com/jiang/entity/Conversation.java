package com.jiang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话实体 — t_conversation
 */
@Data
@TableName("t_conversation")
public class Conversation {

    /** 会话主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 会话标题（首条消息截取前 50 字） */
    private String title;

    /** 使用的模型 */
    private String model;

    /** 消息总数 */
    private Integer messageCount;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
