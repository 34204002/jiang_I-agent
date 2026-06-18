package com.jiang.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 知识图谱概念视图
 */
@Data
public class ConceptVO {

    private String name;
    private String description;
    private String category;
    private Integer difficulty;
    private Integer relationCount;

    /** 前置知识 */
    private List<ConceptRef> prerequisites;

    /** 相关概念 */
    private List<ConceptRef> relatedConcepts;

    /** 关联文档 */
    private List<DocumentRef> documents;

    @Data
    public static class ConceptRef {
        private String name;
        private Integer difficulty;
        private String relation;    // 关系说明
    }

    @Data
    public static class DocumentRef {
        private Long documentId;
        private String filename;
    }
}
