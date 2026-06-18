package com.jiang.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 消息视图
 */
@Data
public class MessageVO {

    /** 消息 ID */
    private Long id;
    /** 角色：user / assistant / tool */
    private String role;
    /** 消息正文 */
    private String content;
    /** 工具调用记录 */
    private List<ToolCall> toolCalls;
    /** Token 消耗估算 */
    private Integer tokenCount;
    /** 消息时间 */
    private LocalDateTime createdAt;

    /**
     * 工具调用
     */
    @Data
    public static class ToolCall {
        /** 工具名称 */
        private String name;
        /** 工具入参 */
        private Map<String, Object> input;
        /** 工具返回 */
        private String output;
    }
}
