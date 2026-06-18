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

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private Long messageId;
    private String toolName;
    private String inputJson;       // JSON string
    private String outputText;
    private Integer durationMs;
    private Integer success;        // 0-失败 1-成功

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
