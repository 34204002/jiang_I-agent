package com.jiang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息实体 — t_message
 */
@Data
@TableName("t_message")
public class Message {

    /**
     * 消息主键，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话 ID
     */
    private Long conversationId;

    /**
     * 角色：user / assistant / tool
     */
    private String role;

    /**
     * 消息正文
     */
    private String content;

    /**
     * 思考内容（DeepSeek reasoning_content）
     */
    private String thinking;

    /**
     * 工具调用记录，JSON 数组
     */
    private String toolCalls;

    /**
     * Token 消耗估算
     */
    private Integer tokenCount;

    /**
     * 消息时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
