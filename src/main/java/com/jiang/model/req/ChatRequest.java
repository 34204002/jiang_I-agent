package com.jiang.model.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话请求
 */
@Data
@NoArgsConstructor
public class ChatRequest {

    /**
     * 用户消息
     */
    private String message;

    /**
     * 会话 ID，不传则自动创建
     */
    private String conversationId;

    /**
     * 附件列表（已上传并解析完成的文件内容）
     */
    private List<Attachment> attachments;

    public ChatRequest(String message, String conversationId) {
        this.message = message;
        this.conversationId = conversationId;
    }

    public ChatRequest(String message, String conversationId, List<Attachment> attachments) {
        this.message = message;
        this.conversationId = conversationId;
        this.attachments = attachments;
    }

    /**
     * 对话附件 —— 前端上传文件后后端解析返回的文本内容
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        /** 文件名 */
        private String filename;
        /** 文件类型（pdf/md/txt/docx） */
        private String fileType;
        /** 已解析的文本内容 */
        private String content;
    }
}
