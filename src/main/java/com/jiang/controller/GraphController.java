package com.jiang.controller;

import com.jiang.common.Result;
import com.jiang.model.vo.ConceptVO;
import com.jiang.model.PageResult;
import com.jiang.model.resp.PathResult;
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
        // TODO: Phase 3 — Neo4j 查询 Concept 节点
        return Result.fail("not implemented");
    }

    /** 概念详情（含前置知识、相关概念、关联文档） */
    @GetMapping("/concepts/{name}")
    public Result<ConceptVO> getConcept(@PathVariable String name) {
        // TODO: Phase 3 — Neo4j 单节点 + 1 跳关系查询
        return Result.fail("not implemented");
    }

    /** 知识链查询 — Neo4j 核心差异化能力 */
    @GetMapping("/concepts/{name}/path")
    public Result<PathResult> findPath(@PathVariable String name,
                                        @RequestParam String target,
                                        @RequestParam(defaultValue = "3") int maxHops) {
        // TODO: Phase 3 — Cypher: MATCH path = (c1)-[:PREREQUISITE_OF*1..{maxHops}]->(c2)
        return Result.fail("not implemented");
    }
}
