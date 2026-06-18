package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.model.PageResult;
import com.jiang.model.resp.PathResult;
import com.jiang.model.vo.ConceptVO;
import com.jiang.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 知识图谱接口 — Phase 3 (Neo4j)
 */
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    /** 概念列表 */
    @GetMapping("/concepts")
    public Result<PageResult<ConceptVO>> listConcepts(@RequestParam(required = false) String category,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return Result.success(graphService.listConcepts(category, page, size));
    }

    /** 概念详情（含前置知识、相关概念、关联文档） */
    @GetMapping("/concepts/{name}")
    public Result<ConceptVO> getConcept(@PathVariable String name) {
        return Result.success(graphService.getConcept(name));
    }

    /** 知识链查询 — Neo4j 核心差异化能力 */
    @GetMapping("/concepts/{name}/path")
    public Result<PathResult> findPath(@PathVariable String name,
                                        @RequestParam String target,
                                        @RequestParam(defaultValue = "3") int maxHops) {
        return Result.success(graphService.findPath(name, target, maxHops));
    }
}
