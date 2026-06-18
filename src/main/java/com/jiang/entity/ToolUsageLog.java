package com.jiang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具调用日志实体 — t_tool_usage_log
 */
@Data
@TableName("t_tool_usage_log")
public class ToolUsageLog {

    /** 日志主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联会话 ID */
    private Long conversationId;

    /** 关联消息 ID */
    private Long messageId;

    /** 工具名称 */
    private String toolName;

    /** 工具入参，JSON */
    private String inputJson;

    /** 工具返回文本 */
    private String outputText;

    /** 执行耗时（毫秒） */
    private Integer durationMs;

    /** 0-失败 1-成功 */
    private Integer success;

    /** 日志时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
