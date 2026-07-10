package com.jiang.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话视图
 */
@Data
public class ConversationVO {

    /**
     * 会话 ID
     */
    private Long id;
    /**
     * 会话标题
     */
    private String title;
    /**
     * 使用的模型
     */
    private String model;
    /**
     * 消息总数
     */
    private Integer messageCount;
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;
}
