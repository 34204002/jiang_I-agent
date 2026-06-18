package com.jiang.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 知识图谱概念视图
 */
@Data
public class ConceptVO {

    /** 概念名称 */
    private String name;
    /** 概念描述 */
    private String description;
    /** 分类 */
    private String category;
    /** 难度等级 */
    private Integer difficulty;
    /** 关联关系数 */
    private Integer relationCount;

    /** 前置知识 */
    private List<ConceptRef> prerequisites;

    /** 相关概念 */
    private List<ConceptRef> relatedConcepts;

    /** 关联文档 */
    private List<DocumentRef> documents;

    /**
     * 概念引用
     */
    @Data
    public static class ConceptRef {
        /** 概念名称 */
        private String name;
        /** 难度等级 */
        private Integer difficulty;
        /** 关系说明 */
        private String relation;
    }

    /**
     * 文档引用
     */
    @Data
    public static class DocumentRef {
        /** 文档 ID */
        private Long documentId;
        /** 文件名 */
        private String filename;
    }
}
