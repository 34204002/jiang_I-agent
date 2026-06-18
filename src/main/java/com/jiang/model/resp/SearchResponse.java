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

    /** 召回的知识来源 */
    private List<Source> sources;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private Long documentId;
        private String filename;
        private Integer chunkIndex;
        private String content;
        private Double score;
    }
}
