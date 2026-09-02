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

    /**
     * 文档 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户 ID（用户隔离）
     */
    private Long userId;

    /**
     * 原始文件名
     */
    private String filename;

    /**
     * 文件类型：md / pdf / txt / docx
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * SHA-256 内容哈希，用于去重
     */
    private String contentHash;

    /**
     * 分片总数
     */
    private Integer chunkCount;

    /**
     * 处理状态：0-待处理 1-已解析 2-已向量化 3-失败
     */
    private Integer status;

    /**
     * 处理失败原因（status=3 时可见）
     */
    private String errorMessage;

    /**
     * AI 摘要
     */
    private String summary;

    /**
     * OSS 存储 key，用于下载原始文件
     */
    private String ossKey;

    /**
     * 上传时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadedAt;
}
