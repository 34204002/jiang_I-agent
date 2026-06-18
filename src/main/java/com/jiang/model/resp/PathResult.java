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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Path {
        private List<Node> nodes;
        private int length;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        private String name;
        private Integer difficulty;
    }
}
