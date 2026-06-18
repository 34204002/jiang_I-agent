package com.jiang.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档视图 — 知识库文档列表/详情用
 */
@Data
public class DocumentVO {

    private Long id;
    private String filename;
    private String fileType;
    private Long fileSize;
    private Integer chunkCount;
    private Integer status;         // 0-待处理 1-已解析 2-已向量化
    private String summary;
    private LocalDateTime uploadedAt;
}
