package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.service.GraphService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱控制器 — 概念 CRUD + 知识链查询（全部按当前用户隔离）。
 */
@Slf4j
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    /**
     * 查询所有概念分类（去重），供前端筛选下拉
     */
    @GetMapping("/categories")
    public Result<List<String>> listCategories(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(graphService.listCategories(userId));
    }

    /**
     * 概念列表（支持搜索和分类筛选）
     */
    @GetMapping("/concepts")
    public Result<Map<String, Object>> listConcepts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(graphService.searchConcepts(keyword, category, page, size, userId));
    }

    /**
     * 概念详情（含前置知识、相关概念、关联文档）
     */
    @GetMapping("/concepts/{name}")
    public Result<GraphService.ConceptDetail> getConcept(@PathVariable String name,
                                                         HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(graphService.getConceptDetail(name, userId));
    }

    /**
     * 图可视化数据（节点+边，用于前端 vis-network 力导向图）
     */
    @GetMapping("/concepts/{name}/graph")
    public Result<GraphService.GraphData> getGraph(@PathVariable String name,
                                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(graphService.getGraph(name, userId));
    }

    /**
     * 知识链查询（学习路径，核心差异化接口）
     */
    @GetMapping("/concepts/{name}/path")
    public Result<GraphService.LearningPath> findPath(
            @PathVariable String name,
            @RequestParam(required = false) String target,
            @RequestParam(defaultValue = "3") int maxHops,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (target != null && !target.isBlank()) {
            return Result.success(graphService.findPath(name, target, maxHops, userId));
        }
        return Result.success(graphService.findPrerequisites(name, maxHops, userId));
    }

    /**
     * 手动添加概念
     */
    @PostMapping("/concepts")
    public Result<Map<String, Object>> addConcept(@RequestBody Map<String, Object> body,
                                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            return Result.fail(400, "概念名称不能为空");
        }
        String description = (String) body.getOrDefault("description", "");
        String category = (String) body.getOrDefault("category", "未分类");
        Integer difficulty = body.get("difficulty") instanceof Number n
                ? n.intValue() : 1;

        graphService.addConcept(name, description, category, difficulty, userId);
        return Result.success(Map.of("name", name, "message", "概念已添加"));
    }

    /**
     * 添加关系
     */
    @PostMapping("/concepts/{name}/relations")
    public Result<Map<String, Object>> addRelation(
            @PathVariable String name,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String target = (String) body.get("target");
        String type = (String) body.getOrDefault("type", "RELATED_TO");
        if (target == null || target.isBlank()) {
            return Result.fail(400, "目标概念不能为空");
        }
        if ("PREREQUISITE_OF".equals(type)) {
            graphService.addPrerequisite(name, target, userId);
        } else {
            graphService.addRelated(name, target, userId);
        }
        return Result.success(Map.of("from", name, "to", target, "type", type, "message", "关系已添加"));
    }

    /**
     * 从文档提取概念
     */
    @PostMapping("/extract")
    public Result<Map<String, Object>> extractFromDocument(@RequestBody Map<String, Object> body,
                                                           HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Object docIdObj = body.get("documentId");
        if (docIdObj == null) {
            return Result.fail(400, "documentId 不能为空");
        }
        Long documentId = docIdObj instanceof Number n ? n.longValue() : Long.parseLong(docIdObj.toString());
        return Result.success(graphService.extractFromDocument(documentId, userId));
    }

    /**
     * 删除概念（级联删除所有关联关系）
     */
    @DeleteMapping("/concepts/{name}")
    public Result<Map<String, Object>> deleteConcept(@PathVariable String name,
                                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        graphService.deleteConcept(name, userId);
        return Result.success(Map.of("name", name, "message", "概念已删除"));
    }

    /**
     * 删除关系
     */
    @DeleteMapping("/concepts/{name}/relations")
    public Result<Map<String, Object>> deleteRelation(
            @PathVariable String name,
            @RequestParam String target,
            @RequestParam(defaultValue = "RELATED_TO") String type,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        graphService.deleteRelation(name, target, type, userId);
        return Result.success(Map.of("from", name, "to", target, "type", type, "message", "关系已删除"));
    }
}
