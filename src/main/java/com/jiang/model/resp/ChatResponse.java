package com.jiang.model.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** AI 回复内容 */
    private String content;

    /** 会话 ID */
    private String conversationId;

    /** 本次调用的工具名称列表 */
    private List<String> toolsCalled;
}
