package com.jiang.model.req;

import lombok.Data;

/**
 * RAG 知识库检索请求
 */
@Data
public class SearchRequest {

    /**
     * 自然语言查询
     */
    private String query;

    /**
     * 返回结果数，默认 5
     */
    private Integer topK;
}
