package com.jiang.model.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** 用户消息 */
    private String message;

    /** 会话 ID，不传则自动创建 */
    private String conversationId;
}
