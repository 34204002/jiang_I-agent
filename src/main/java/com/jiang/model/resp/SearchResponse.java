package com.jiang.model.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 知识库检索响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    /** LLM 增强后的回答 */
    private String answer;

    /** 召回的知识来源列表 */
    private List<Source> sources;

    /**
     * 召回来源
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        /** 文档 ID */
        private Long documentId;
        /** 文件名 */
        private String filename;
        /** 分片序号 */
        private Integer chunkIndex;
        /** 分片文本 */
        private String content;
        /** 相似度分数 */
        private Double score;
    }
}
