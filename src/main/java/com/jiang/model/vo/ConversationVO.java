package com.jiang.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话视图
 */
@Data
public class ConversationVO {

    private Long id;
    private String title;
    private String model;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
