package com.jiang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档实体 — t_document
 */
@Data
@TableName("t_document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String filename;
    private String fileType;
    private Long fileSize;
    private String contentHash;
    private Integer chunkCount;
    private Integer status;         // 0-待处理 1-已解析 2-已向量化
    private String summary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadedAt;
}
