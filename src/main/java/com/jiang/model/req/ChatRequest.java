package com.jiang.model.req;

import lombok.Data;

/**
 * 对话请求
 */
@Data
public class ChatRequest {

    /** 用户消息 */
    private String message;

    /** 会话 ID，不传则自动创建 */
    private String conversationId;
}
