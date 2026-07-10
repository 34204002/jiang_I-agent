package com.jiang.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiang.service.GraphService;
import com.jiang.service.GraphService.LearningPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 知识图谱工具 — 让 Agent 查询和构建知识图谱。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final GraphService graphService;

    @Tool(name = "search_concepts",
            description = "搜索知识图谱中的概念。当用户问「XX是什么」「有哪些概念」「XX的前置知识」「知识图谱里有没有」时使用。"
                    + "返回概念的描述、分类、难度和关系信息。",
            parameters = """
                    {
                        "type": "object",
                        "properties": {
                            "keyword": {"type": "string", "description": "搜索关键词，不传则返回全部概念"},
                            "category": {"type": "string", "description": "按分类筛选，不传则不限制"}
                        },
                        "required": []
                    }
                    """)
    public String searchConcepts(String keyword, String category) {
        var result = graphService.searchConcepts(
                keyword != null ? keyword : "",
                category, 1, 20);

        int total = (int) result.get("total");
        var records = (java.util.List<?>) result.get("records");

        if (records.isEmpty()) {
            String hint = (keyword != null && !keyword.isBlank())
                    ? "未找到与「" + keyword + "」相关的概念。"
                    : "知识图谱中还没有任何概念。";
            return hint + " 你可以通过 add_concept 工具添加新概念。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(total).append(" 个概念:\n\n");
        for (Object obj : records) {
            var m = (java.util.Map<?, ?>) obj;
            String stars = "⭐".repeat(Math.min(5, (int) m.get("difficulty")));
            sb.append("  📘 ").append(m.get("name"))
                    .append(" [").append(m.get("category")).append("] ").append(stars)
                    .append("\n     ").append(m.get("description"))
                    .append("\n     ").append(m.get("relationCount")).append(" 个关联\n\n");
        }
        return sb.toString();
    }

    @Tool(name = "find_learning_path",
            description = "查询两个概念之间的学习路径。当用户问「学XX需要先学什么」「从A到B怎么学」「XX的前置知识有哪些」时使用。"
                    + "返回从前置概念到目标概念的路径链。",
            parameters = """
                    {
                        "type": "object",
                        "properties": {
                            "from": {"type": "string", "description": "起始概念名称（通常是基础概念）"},
                            "to": {"type": "string", "description": "目标概念名称（用户想学的）"}
                        },
                        "required": ["from", "to"]
                    }
                    """)
    public String findLearningPath(String from, String to) {
        try {
            LearningPath paths = graphService.findPath(from, to, 5);
            if (paths.isEmpty()) {
                return "未找到从「" + from + "」到「" + to + "」的学习路径。"
                        + "建议先用 search_concepts 确认概念是否存在，或用 add_concept 补充概念和关系。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("从「").append(from).append("」到「").append(to).append("」的学习路径:\n\n");
            int idx = 1;
            for (var path : paths.paths()) {
                sb.append("路径 ").append(idx++).append(" (共 ").append(path.size()).append(" 步):\n");
                String chain = path.stream()
                        .map(n -> n.name() + " (⭐" + n.difficulty() + ")")
                        .collect(Collectors.joining(" → "));
                sb.append("  ").append(chain).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询学习路径失败: " + e.getMessage() + "。可能概念名称不完全匹配，请用 search_concepts 确认。";
        }
    }

    @Tool(name = "add_concept",
            description = "向知识图谱添加概念及关系。支持单概念（name 参数）和批量概念（concepts 数组）。"
                    + "当讨论到新技术概念，或用户说「帮我记一下」「这个知识点」时主动记录。"
                    + "多个概念时优先使用 concepts 参数一次性传入。",
            parameters = """
                    {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string", "description": "概念名称（单概念模式）"},
                            "description": {"type": "string", "description": "概念的一句话描述"},
                            "category": {"type": "string", "description": "分类，如「中间件」「编程语言」「算法」"},
                            "difficulty": {"type": "integer", "description": "难度 1-5，1=入门 5=专家"},
                            "prerequisiteOf": {"type": "string", "description": "前置关系：name 是 prerequisiteOf 的前置知识"},
                            "relatedTo": {"type": "string", "description": "相关关系：name 与 relatedTo 相关"},
                            "concepts": {"type": "array", "description": "批量添加的概念列表（优先于单概念参数）",
                                "items": {"type": "object", "properties": {
                                    "name": {"type": "string", "description": "概念名称"},
                                    "description": {"type": "string", "description": "描述"},
                                    "category": {"type": "string", "description": "分类"},
                                    "difficulty": {"type": "integer", "description": "难度"},
                                    "prerequisiteOf": {"type": "string", "description": "前置关系"},
                                    "relatedTo": {"type": "string", "description": "相关关系"}
                                }, "required": ["name"]}}
                        },
                        "required": []
                    }
                    """)
    public String addConcept(String name, String description, String category, Integer difficulty,
                             String prerequisiteOf, String relatedTo, String concepts) {

        // 批量模式优先
        if (concepts != null && !concepts.isBlank()) {
            try {
                JsonNode arr = MAPPER.readTree(concepts);
                if (!arr.isArray()) return "concepts 参数必须是数组格式。";

                int added = 0;
                for (JsonNode item : arr) {
                    String cn = item.path("name").asText().trim();
                    if (cn.isBlank()) continue;
                    String cd = normalizeString(item, "description");
                    String cc = normalizeString(item, "category");
                    int cdif = normalizeDifficulty(item.path("difficulty"));
                    String cpo = normalizeString(item, "prerequisiteOf");
                    String crt = normalizeString(item, "relatedTo");

                    addSingleConcept(cn, cd != null ? cd : "",
                            cc != null ? cc : "未分类",
                            Math.min(5, Math.max(1, cdif)), cpo, crt);
                    added++;
                }
                return "已批量记录 " + added + " 个概念到知识图谱。";
            } catch (Exception e) {
                return "批量添加失败: " + e.getMessage();
            }
        }

        // 单概念模式
        return addSingleConcept(name != null ? name.trim() : "",
                description != null ? description : "",
                category != null ? category : "未分类",
                difficulty != null ? Math.min(5, Math.max(1, difficulty)) : 1,
                normalizeStringValue(prerequisiteOf),
                normalizeStringValue(relatedTo));
    }

    /**
     * 从 JSON 中提取字符串：支持 string、array（取首个）、空数组→null
     */
    private String normalizeString(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        return normalizeStringValue(node);
    }

    private String normalizeStringValue(Object raw) {
        if (raw == null) return null;
        if (raw instanceof JsonNode node) return normalizeStringNode(node);
        if (raw instanceof String s) return s.isBlank() ? null : s.trim();
        return raw.toString();
    }

    private String normalizeStringNode(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            if (node.size() == 0) return null;
            return node.get(0).asText().trim();
        }
        String s = node.asText();
        return s.isBlank() ? null : s.trim();
    }

    /**
     * 容错难度值：支持整数、字符串("MEDIUM"→3、"EASY"→1、"HARD"→5)、非数字字符串→2
     */
    private int normalizeDifficulty(JsonNode node) {
        if (node == null || node.isNull()) return 2;
        if (node.isInt()) return node.asInt();
        try {
            return Integer.parseInt(node.asText().trim());
        } catch (NumberFormatException e) {
            String s = node.asText().toLowerCase().trim();
            return switch (s) {
                case "easy", "简单", "入门", "初级", "beginner", "1" -> 1;
                case "hard", "困难", "高级", "expert", "5" -> 5;
                default -> 2; // medium, 中等, 中级, 未识别 → 默认2
            };
        }
    }

    private String addSingleConcept(String name, String description, String category, int difficulty,
                                    String prerequisiteOf, String relatedTo) {
        if (name == null || name.isBlank()) return "概念名称不能为空。";

        var c = graphService.addConcept(name,
                description != null ? description : "",
                category != null ? category : "未分类",
                difficulty);

        StringBuilder sb = new StringBuilder();
        sb.append("已记录概念: ").append(name)
                .append(" (").append(c.getCategory()).append(", ⭐").append(c.getDifficulty()).append(")");

        if (prerequisiteOf != null && !prerequisiteOf.isBlank()) {
            try {
                graphService.addPrerequisite(name, prerequisiteOf);
                sb.append("\n  关系: ").append(name).append(" --[前置知识]--> ").append(prerequisiteOf);
            } catch (Exception e) {
                sb.append("\n  (关系添加失败: ").append(e.getMessage()).append(")");
            }
        }
        if (relatedTo != null && !relatedTo.isBlank()) {
            try {
                graphService.addRelated(name, relatedTo);
                sb.append("\n  关系: ").append(name).append(" --[相关]--> ").append(relatedTo);
            } catch (Exception e) {
                sb.append("\n  (关系添加失败: ").append(e.getMessage()).append(")");
            }
        }

        return sb.toString();
    }
}
