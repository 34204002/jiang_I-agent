package com.jiang.service;

import com.jiang.model.vo.ConceptVO;
import com.jiang.model.resp.PathResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识图谱服务 — Neo4j 概念 + 关系查询（Phase 3）
 */
@Service
public class GraphService {

    /**
     * 按分类分页查询概念列表
     */
    public List<ConceptVO> listConcepts(String category, int page, int size) {
        // TODO: Phase 3 — Cypher 查询 (:Concept)-[:BELONGS_TO]->(:Category)
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 查询概念详情：基本信息 + 前置知识 + 相关概念 + 关联文档
     */
    public ConceptVO getConcept(String name) {
        // TODO: Phase 3 — 单节点 + 1 跳关系 Cypher
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 查询从 start 到 target 的知识学习路径（BFS/DFS）
     */
    public PathResult findPath(String start, String target, int maxHops) {
        // TODO: Phase 3
        // MATCH path = (c1:Concept {name: start})-[:PREREQUISITE_OF*1..maxHops]->(c2:Concept {name: target})
        // RETURN path
        throw new UnsupportedOperationException("not implemented");
    }
}
