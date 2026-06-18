package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.model.PageResult;
import com.jiang.model.req.SearchRequest;
import com.jiang.model.resp.SearchResponse;
import com.jiang.model.vo.DocumentVO;
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
        return Result.success(knowledgeService.upload(file));
    }

    /** 文档列表 */
    @GetMapping("/documents")
    public Result<PageResult<DocumentVO>> listDocuments(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return Result.success(knowledgeService.listDocuments(page, size));
    }

    /** 删除文档（同时清除向量库） */
    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return Result.success();
    }

    /** RAG 知识库问答 */
    @PostMapping("/search")
    public Result<SearchResponse> search(@RequestBody SearchRequest request) {
        return Result.success(knowledgeService.search(request));
    }
}
