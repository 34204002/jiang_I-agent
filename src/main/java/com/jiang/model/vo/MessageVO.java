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

    private Long id;
    private String role;            // user / assistant / tool
    private String content;
    private List<ToolCall> toolCalls;
    private Integer tokenCount;
    private LocalDateTime createdAt;

    @Data
    public static class ToolCall {
        private String name;
        private Map<String, Object> input;
        private String output;
    }
}
