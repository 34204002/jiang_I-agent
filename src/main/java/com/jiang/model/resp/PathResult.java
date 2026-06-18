package com.jiang.model.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识链路径查询结果（Neo4j 核心差异化能力）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PathResult {

    /** 找到的路径列表 */
    private List<Path> paths;

    /**
     * 一条知识链路径
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Path {
        /** 路径上的节点序列 */
        private List<Node> nodes;
        /** 路径长度（跳数） */
        private int length;
    }

    /**
     * 知识图谱节点
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        /** 概念名称 */
        private String name;
        /** 难度等级 */
        private Integer difficulty;
    }
}
