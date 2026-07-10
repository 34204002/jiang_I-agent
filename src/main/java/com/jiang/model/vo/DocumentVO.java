package com.jiang.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档视图 — 知识库文档列表/详情用
 */
@Data
public class DocumentVO {

    /**
     * 文档 ID
     */
    private Long id;
    /**
     * 文件名
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
     * 分片数量
     */
    private Integer chunkCount;
    /**
     * 处理状态：0-待处理 1-已解析 2-已向量化
     */
    private Integer status;
    /**
     * LLM 生成的文档摘要
     */
    private String summary;
    /**
     * 上传时间
     */
    private LocalDateTime uploadedAt;
    /**
     * 下载链接（OSS 公网地址）
     */
    private String downloadUrl;
}
