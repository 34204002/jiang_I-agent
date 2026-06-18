package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.model.vo.DocumentVO;
import com.jiang.model.PageResult;
import com.jiang.model.req.SearchRequest;
import com.jiang.model.resp.SearchResponse;
import com.jiang.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库接口 — Phase 2 (RAG)
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /** 上传文档 */
    @PostMapping("/documents")
    public Result<DocumentVO> upload(@RequestParam MultipartFile file) {
        // TODO: Phase 2 — 文档解析 + 分片 + 向量化
        return Result.fail("not implemented");
    }

    /** 文档列表 */
    @GetMapping("/documents")
    public Result<PageResult<DocumentVO>> listDocuments(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        // TODO: Phase 2 — 分页查询 t_document
        return Result.fail("not implemented");
    }

    /** 删除文档（同时清除向量库） */
    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        // TODO: Phase 2 — 删 MySQL 记录 + Qdrant 向量
        return Result.fail("not implemented");
    }

    /** RAG 知识库问答 */
    @PostMapping("/search")
    public Result<SearchResponse> search(@RequestBody SearchRequest request) {
        // TODO: Phase 2 — 向量检索 + LLM 增强回答
        return Result.fail("not implemented");
    }
}
