package com.jiang.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档分片实体 — t_document_chunk
 */
@Data
@TableName("t_document_chunk")
public class DocumentChunk {

    /**
     * 分片 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属文档 ID
     */
    private Long documentId;

    /**
     * 分片序号（从 0 开始）
     */
    private Integer chunkIndex;

    /**
     * 分片文本内容
     */
    private String content;

    /**
     * Token 估算数量
     */
    private Integer tokenCount;

    /**
     * Qdrant Point ID（向量存储后回写）
     */
    private String embeddingId;
}
