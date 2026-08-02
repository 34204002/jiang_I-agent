package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.model.PageResult;
import com.jiang.model.req.SearchRequest;
import com.jiang.model.resp.SearchResponse;
import com.jiang.model.vo.DocumentVO;
import com.jiang.service.KnowledgeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 知识库接口 — 文档上传/列表/删除/下载/检索（全部按当前用户隔离）。
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 上传单个文档
     */
    @PostMapping("/documents")
    public Result<DocumentVO> upload(@RequestParam MultipartFile file,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return Result.success(knowledgeService.upload(file, userId));
        } catch (IOException e) {
            log.error("文档上传失败", e);
            return Result.fail(500, "文档解析失败: " + e.getMessage());
        }
    }

    /**
     * 批量上传文档
     */
    @PostMapping("/documents/batch")
    public Result<List<DocumentVO>> batchUpload(@RequestParam List<MultipartFile> files,
                                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(knowledgeService.batchUpload(files, userId));
    }

    /**
     * 文档列表（当前用户）
     */
    @GetMapping("/documents")
    public Result<PageResult<DocumentVO>> listDocuments(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(knowledgeService.listDocuments(page, size, userId));
    }

    /**
     * 下载原始文件（重定向到 OSS）
     */
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Void> download(@PathVariable Long id,
                                         HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String url = knowledgeService.getDownloadUrl(id, userId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    /**
     * 删除文档（同时清除向量库，仅本人可删）
     */
    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        knowledgeService.deleteDocument(id, userId);
        return Result.success();
    }

    /**
     * RAG 知识库问答（仅检索当前用户的文档）
     */
    @PostMapping("/search")
    public Result<SearchResponse> search(@RequestBody SearchRequest request,
                                         HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        return Result.success(knowledgeService.search(request, userId));
    }
}
