package com.jiang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 文档分片实体 — t_document_chunk
 */
@Data
@TableName("t_document_chunk")
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private String embeddingId;     // Qdrant point ID
}
