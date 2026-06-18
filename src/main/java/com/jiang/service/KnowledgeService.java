package com.jiang.service;

import com.jiang.model.vo.DocumentVO;
import com.jiang.model.resp.SearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库服务 — RAG 检索 + 文档管理（Phase 2）
 */
@Service
public class KnowledgeService {

    /**
     * 上传并解析文档 → 分片 → 向量化存入 Qdrant
     *
     * @param file 上传文件（md/txt/pdf）
     * @return 文档元数据
     */
    public DocumentVO upload(MultipartFile file) {
        // TODO: Phase 2
        // 1. 文件校验（类型、大小）
        // 2. SHA-256 去重（查 t_document.content_hash）
        // 3. Tika 解析文本
        // 4. 文本分片（TokenTextSplitter）
        // 5. Embedding 向量化 + Qdrant 写入
        // 6. 入库 t_document + t_document_chunk
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 分页查询文档列表
     */
    public List<DocumentVO> listDocuments(int page, int size) {
        // TODO: Phase 2 — MyBatis-Plus 分页查 t_document
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 删除文档，同步清除 Qdrant 向量 + MySQL 分片
     */
    public void deleteDocument(Long id) {
        // TODO: Phase 2 — 事务：删 t_document_chunk → 删 Qdrant points → 删 t_document
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * RAG 语义检索 + LLM 增强回答
     *
     * @param query 自然语言查询
     * @param topK  召回片段数
     * @return LLM 增强回答 + 来源
     */
    public SearchResponse search(String query, int topK) {
        // TODO: Phase 2
        // 1. query → Embedding 向量化
        // 2. Qdrant.search() 语义检索 Top-K
        // 3. 拼接 Prompt（检索结果 + 原始问题）
        // 4. ChatClient 生成增强回答
        // 5. 组装 SearchResponse（answer + sources）
        throw new UnsupportedOperationException("not implemented");
    }
}
