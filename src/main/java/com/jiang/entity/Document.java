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

    /** 文档主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名 */
    private String filename;

    /** 文件类型：md / pdf / txt / docx */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** SHA-256 内容哈希，用于去重 */
    private String contentHash;

    /** 分片数量 */
    private Integer chunkCount;

    /** 处理状态：0-待处理 1-已解析 2-已向量化 */
    private Integer status;

    /** LLM 生成的文档摘要 */
    private String summary;

    /** 上传时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadedAt;
}
