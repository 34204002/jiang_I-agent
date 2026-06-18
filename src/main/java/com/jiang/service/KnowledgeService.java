package com.jiang.service;

import com.jiang.model.PageResult;
import com.jiang.model.req.SearchRequest;
import com.jiang.model.resp.SearchResponse;
import com.jiang.model.vo.DocumentVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库服务 — RAG 检索 + 文档管理（Phase 2）
 */
@Service
public class KnowledgeService {

    /**
     * 上传并解析文档 → 分片 → 向量化存入 Qdrant
     */
    public DocumentVO upload(MultipartFile file) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 分页查询文档列表
     */
    public PageResult<DocumentVO> listDocuments(int page, int size) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 删除文档，同步清除 Qdrant 向量 + MySQL 分片
     */
    public void deleteDocument(Long id) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * RAG 语义检索 + LLM 增强回答
     */
    public SearchResponse search(SearchRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }
}
