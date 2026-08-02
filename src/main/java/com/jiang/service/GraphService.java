package com.jiang.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiang.entity.ConceptEntity;
import com.jiang.entity.DocumentChunk;
import com.jiang.mapper.DocumentChunkMapper;
import com.jiang.mapper.DocumentMapper;
import com.jiang.repository.ConceptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱服务 — 概念 CRUD + Cypher 路径查询 + AI 文档概念提取。
 * <p>
 * 用户隔离：所有操作按 (name, userId) 归属当前用户，不同用户拥有独立概念图。
 * 复杂路径查询走 {@link Neo4jClient}，简单 CRUD 走 {@link ConceptRepository}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional
public class GraphService {

    /**
     * 干净的 ObjectMapper —— RedisConfig 里那个 Bean 开了 DefaultTyping，
     * 序列化会带 @class 注解，污染发往 LLM 的请求体（见 ISSUES.md #5）。
     * 凡涉及 API 请求构造一律用此实例。
     */
    private static final ObjectMapper CLEAN_MAPPER = new ObjectMapper();
    private final ConceptRepository conceptRepo;
    private final Neo4jClient neo4jClient;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    @Value("${spring.ai.openai.chat.model:deepseek-v4-flash}")
    private String defaultModel;
    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    // ==================== 查询 ====================

    /**
     * 分页搜索概念（当前用户）
     */
    public Map<String, Object> searchConcepts(String keyword, String category, int page, int size, Long userId) {
        int skip = (page - 1) * size;
        List<ConceptEntity> list;
        int total;

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = category != null && !category.isBlank();

        if (hasKeyword) {
            String regex = buildFuzzyRegex(keyword);
            list = conceptRepo.searchByName(userId, regex, skip, size);
            total = conceptRepo.countByName(userId, regex);
        } else if (hasCategory) {
            list = conceptRepo.findByCategoryPaged(userId, category, skip, size);
            total = conceptRepo.countByCategory(userId, category);
        } else {
            list = conceptRepo.findAllPaged(userId, skip, size);
            total = (int) conceptRepo.countByUserId(userId);
        }

        List<Map<String, Object>> records = list.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.getName());
            m.put("description", c.getDescription());
            m.put("category", c.getCategory());
            m.put("difficulty", c.getDifficulty());
            m.put("relationCount", (c.getPrerequisites() != null ? c.getPrerequisites().size() : 0)
                    + (c.getRelated() != null ? c.getRelated().size() : 0));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("records", records);
        return result;
    }

    /**
     * 查询所有分类（去重，过滤空值），供前端筛选下拉（当前用户）
     */
    public List<String> listCategories(Long userId) {
        try {
            return conceptRepo.findAllCategories(userId).stream()
                    .filter(c -> c != null && !c.isBlank())
                    .distinct()
                    .sorted()
                    .toList();
        } catch (Exception e) {
            log.warn("查询分类失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 概念详情（含关系，当前用户）
     */
    public ConceptDetail getConceptDetail(String name, Long userId) {
        ConceptEntity c = conceptRepo.findByNameAndUserId(name, userId)
                .orElseThrow(() -> new NoSuchElementException("概念不存在: " + name));

        List<ConceptRef> prerequisites = conceptRepo.findDirectPrerequisites(userId, name)
                .stream().map(p -> new ConceptRef(p.getName(), "前置知识", p.getDifficulty()))
                .collect(Collectors.toList());

        List<ConceptRef> related = conceptRepo.findDirectRelated(userId, name)
                .stream().map(r -> new ConceptRef(r.getName(), "相关概念", r.getDifficulty()))
                .collect(Collectors.toList());

        List<DocRef> documents = new ArrayList<>();
        if (c.getDocumentIds() != null) {
            for (Long docId : c.getDocumentIds()) {
                try {
                    var doc = documentMapper.selectById(docId);
                    if (doc != null && userId.equals(doc.getUserId())) {
                        documents.add(new DocRef(docId, doc.getFilename()));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return new ConceptDetail(
                c.getName(), c.getDescription(), c.getCategory(), c.getDifficulty(),
                prerequisites, related, documents);
    }

    /**
     * 学习路径查询（核心差异化，当前用户）。
     * 先用 PREREQUISITE_OF 查，无结果则用 RELATED_TO，再无结果则不限关系类型。
     */
    @SuppressWarnings("unchecked")
    public LearningPath findPath(String from, String to, int maxHops, Long userId) {
        // 尝试 1: PREREQUISITE_OF
        var paths = findPathWithRel(from, to, maxHops, "PREREQUISITE_OF", userId);
        if (!paths.isEmpty()) return new LearningPath(paths);

        // 尝试 2: RELATED_TO
        paths = findPathWithRel(from, to, maxHops, "RELATED_TO", userId);
        if (!paths.isEmpty()) return new LearningPath(paths);

        // 尝试 3: 不限关系类型
        paths = findPathWithRel(from, to, maxHops, null, userId);
        return new LearningPath(paths);
    }

    @SuppressWarnings("unchecked")
    private List<List<PathNode>> findPathWithRel(String from, String to, int maxHops, String relType, Long userId) {
        // 先解析概念名（用户可能输入部分名称，模糊匹配精确概念名）
        String resolvedFrom = resolveName(from, userId);
        String resolvedTo = resolveName(to, userId);
        if (resolvedFrom == null || resolvedTo == null) return List.of();

        // 关系类型仅允许内部枚举常量，防止注入
        String safeRel = (relType != null && (relType.equals("PREREQUISITE_OF") || relType.equals("RELATED_TO")))
                ? relType : null;
        String relPattern = safeRel != null ? ":" + safeRel : "";
        String cypher = """
                MATCH path = shortestPath((start:Concept {name: $from, userId: $userId})-[%s*..%d]-(end:Concept {name: $to, userId: $userId}))
                RETURN nodes(path) AS nodes
                """.formatted(relPattern, maxHops);

        try {
            var results = neo4jClient.query(cypher)
                    .bind(resolvedFrom).to("from")
                    .bind(resolvedTo).to("to")
                    .bind(userId).to("userId")
                    .fetch()
                    .all();

            List<List<PathNode>> paths = new ArrayList<>();
            for (var row : results) {
                List<ConceptEntity> nodes = (List<ConceptEntity>) row.get("nodes");
                if (nodes != null) {
                    List<PathNode> pathNodes = nodes.stream()
                            .map(n -> new PathNode(n.getName(), n.getDifficulty() != null ? n.getDifficulty() : 1))
                            .collect(Collectors.toList());
                    paths.add(pathNodes);
                }
            }
            return paths;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 查询概念的前置知识链（多跳，先 PREREQUISITE_OF 再 RELATED_TO，当前用户）
     */
    @SuppressWarnings("unchecked")
    public LearningPath findPrerequisites(String name, int maxHops, Long userId) {
        var paths = findPrereqWithRel(name, maxHops, "PREREQUISITE_OF", userId);
        if (!paths.isEmpty()) return new LearningPath(paths);
        paths = findPrereqWithRel(name, maxHops, "RELATED_TO", userId);
        return new LearningPath(paths);
    }

    @SuppressWarnings("unchecked")
    private List<List<PathNode>> findPrereqWithRel(String name, int maxHops, String relType, Long userId) {
        String resolved = resolveName(name, userId);
        if (resolved == null) return List.of();
        String cypher = ("MATCH path = (c:Concept {name: $name, userId: $userId})-[:%s*1..%d]->(target) RETURN nodes(path) AS nodes")
                .formatted(relType, maxHops);
        try {
            var results = neo4jClient.query(cypher)
                    .bind(resolved).to("name")
                    .bind(userId).to("userId")
                    .fetch()
                    .all();
            List<List<PathNode>> paths = new ArrayList<>();
            for (var row : results) {
                List<ConceptEntity> nodes = (List<ConceptEntity>) row.get("nodes");
                if (nodes != null) {
                    paths.add(nodes.stream()
                            .map(n -> new PathNode(n.getName(), n.getDifficulty() != null ? n.getDifficulty() : 1))
                            .collect(Collectors.toList()));
                }
            }
            return paths;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ==================== 写入 ====================

    /**
     * 添加/更新概念（当前用户）
     */
    public ConceptEntity addConcept(String name, String description, String category, Integer difficulty, Long userId) {
        return conceptRepo.upsert(name, userId,
                description != null ? description : "",
                category != null ? category : "未分类",
                difficulty != null ? difficulty : 1);
    }

    /**
     * 添加前置关系（含校验 + 传递化简，当前用户）
     */
    public void addPrerequisite(String from, String to, Long userId) {
        String err = validateRelationship(from, to, "PREREQUISITE_OF", userId);
        if (err != null) throw new IllegalArgumentException(err);
        ensureExists(from, userId);
        ensureExists(to, userId);
        conceptRepo.addPrerequisite(userId, from, to);
        log.info("图谱关系: {} --[PREREQUISITE_OF]--> {} (user={})", from, to, userId);
        removeTransitiveRedundancy(from, to, userId);
    }

    /**
     * 添加相关关系（含校验 + 限流，当前用户）
     */
    public void addRelated(String from, String to, Long userId) {
        String err = validateRelationship(from, to, "RELATED_TO", userId);
        if (err != null) throw new IllegalArgumentException(err);
        ensureExists(from, userId);
        ensureExists(to, userId);
        conceptRepo.addRelated(userId, from, to);
        log.info("图谱关系: {} --[RELATED_TO]--> {} (user={})", from, to, userId);
    }

    /**
     * 关系校验：自环、循环（PREREQUISITE_OF 仅限 DAG）、重复关系（当前用户）。
     *
     * @return 错误消息，null 表示通过
     */
    public String validateRelationship(String from, String to, String type, Long userId) {
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            return "概念名称不能为空";
        }
        // 自环
        if (from.equals(to)) {
            return "不能添加自环：" + from + " → " + to;
        }
        // 循环检测：添加 A→B 前，检查是否已有 B→A 路径
        if ("PREREQUISITE_OF".equals(type)) {
            var paths = findPathWithRel(to, from, 10, "PREREQUISITE_OF", userId);
            if (!paths.isEmpty()) {
                return to + " 已经是 " + from + " 的前置知识（直接或间接），再添加反向关系会导致循环";
            }
        }
        return null;
    }

    /**
     * 传递化简：添加 PREQ(A,B) 后，自动删除可通过传递推导的冗余边（当前用户）。
     * <p>
     * 方向1：查 B 的所有直接前置 C（即 PREQ(B,C)），若 PREQ(A,C) 存在 → 冗余（A→B→C 可推导 A→C）
     * 方向2：查 A 的所有直接前置 P（即 PREQ(P,A)），若 PREQ(P,B) 存在 → 冗余（P→A→B 可推导 P→B）
     */
    private void removeTransitiveRedundancy(String from, String to, Long userId) {
        int removed = 0;
        try {
            // 方向1: 查 B 是哪些概念的前置 → 看 A 是否已有直达边
            for (var c : conceptRepo.findDirectPrerequisites(userId, to)) {
                String target = c.getName();
                if (!target.equals(from) && conceptRepo.hasRelation(userId, from, target, "PREREQUISITE_OF")) {
                    conceptRepo.deleteRelation(userId, from, target, "PREREQUISITE_OF");
                    log.info("传递化简: 移除冗余边 {} → {}（可通过 {}→{}→{} 推导）",
                            from, target, from, to, target);
                    removed++;
                }
            }
            // 方向2: 查 A 有哪些前置 → 看 P 是否现在也连到了 B
            for (var p : conceptRepo.findDirectPrerequisites(userId, from)) {
                String source = p.getName();
                if (!source.equals(to) && conceptRepo.hasRelation(userId, source, to, "PREREQUISITE_OF")) {
                    conceptRepo.deleteRelation(userId, source, to, "PREREQUISITE_OF");
                    log.info("传递化简: 移除冗余边 {} → {}（可通过 {}→{}→{} 推导）",
                            source, to, source, from, to);
                    removed++;
                }
            }
        } catch (Exception e) {
            log.warn("传递化简出错（不影响主流程）: {}", e.getMessage());
        }
        if (removed > 0) {
            log.info("传递化简完成: 共移除 {} 条冗余前置边", removed);
        }
    }

    /**
     * 关联文档（当前用户）
     */
    public void linkDocument(String conceptName, Long documentId, Long userId) {
        conceptRepo.linkDocument(userId, conceptName, documentId);
    }

    /**
     * 删除概念（级联删除所有关联关系，当前用户）
     */
    public void deleteConcept(String name, Long userId) {
        if (conceptRepo.findByNameAndUserId(name, userId).isEmpty()) {
            throw new NoSuchElementException("概念不存在: " + name);
        }
        conceptRepo.deleteByNameAndUserId(name, userId);
        log.info("图谱: 已删除概念 {} (user={})", name, userId);
    }

    /**
     * 删除关系（当前用户）
     */
    public void deleteRelation(String from, String to, String type, Long userId) {
        conceptRepo.deleteRelation(userId, from, to, type != null ? type : "RELATED_TO");
        log.info("图谱: 已删除关系 {} --[{}]--> {} (user={})", from, type, to, userId);
    }

    // ==================== 图可视化 ====================

    public GraphData getGraph(String name, Long userId) {
        String resolved = resolveName(name, userId);
        if (resolved == null) return new GraphData(List.of(), List.of());

        // 查目标概念本身
        var self = conceptRepo.findByNameAndUserId(resolved, userId).orElse(null);
        if (self == null) return new GraphData(List.of(), List.of());

        // 查邻居
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) neo4jClient.query("""
                        MATCH (c:Concept {name: $name, userId: $userId})-[r]-(neighbor:Concept {userId: $userId})
                        RETURN type(r) AS rel, neighbor.name AS neighbor,
                               neighbor.difficulty AS diff, neighbor.category AS cat
                        """)
                .bind(resolved).to("name")
                .bind(userId).to("userId")
                .fetch().all();

        Set<String> seen = new LinkedHashSet<>();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        // 中心节点
        nodes.add(new GraphNode(self.getName(), self.getCategory() != null ? self.getCategory() : "",
                self.getDifficulty() != null ? self.getDifficulty() : 1, true));
        seen.add(self.getName());

        // 邻居节点 + 边
        for (var row : rows) {
            String nb = (String) row.get("neighbor");
            String rel = (String) row.get("rel");
            Long diff = row.get("diff") instanceof Number dn ? dn.longValue() : 1L;
            String cat = (String) row.get("cat");

            edges.add(new GraphEdge(self.getName(), nb, rel != null ? rel : "RELATED_TO"));

            if (!seen.contains(nb)) {
                nodes.add(new GraphNode(nb, cat != null ? cat : "", diff.intValue(), false));
                seen.add(nb);
            }
        }

        return new GraphData(nodes, edges);
    }

    /**
     * 解析概念名：先精确匹配，失败则模糊搜索取 top1（当前用户）
     */
    private String resolveName(String name, Long userId) {
        if (name == null || name.isBlank()) return null;
        if (conceptRepo.findByNameAndUserId(name, userId).isPresent()) return name; // 精确匹配
        String regex = buildFuzzyRegex(name);
        var list = conceptRepo.searchByName(userId, regex, 0, 1);
        if (!list.isEmpty()) return list.get(0).getName();
        return null; // 完全找不到
    }

    /**
     * 构建模糊匹配正则：在每对相邻字符间插入 .*，手动转义正则特殊字符
     */
    private String buildFuzzyRegex(String keyword) {
        if (keyword == null || keyword.isBlank()) return ".*";
        StringBuilder sb = new StringBuilder("(?i).*");
        for (char c : keyword.trim().toCharArray()) {
            // 手动转义正则特殊字符（不能用 Pattern.quote，Neo4j 不认 \Q\E）
            switch (c) {
                case '.', '*', '+', '?', '[', ']', '(', ')', '{', '}', '\\', '|', '^', '$' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
            sb.append(".*");
        }
        return sb.toString();
    }

    private void ensureExists(String name, Long userId) {
        if (conceptRepo.findByNameAndUserId(name, userId).isEmpty()) {
            conceptRepo.upsert(name, userId, "", "未分类", 1);
        }
    }

    /**
     * 从已上传的知识库文档中提取概念和关系，写入图谱（当前用户）。
     * <p>
     * 流程：读文档全部 chunk → LLM 提取 → parse JSON → merge 节点+关系。
     */
    public Map<String, Object> extractFromDocument(Long documentId, Long userId) {
        var doc = documentMapper.selectById(documentId);
        if (doc == null) throw new NoSuchElementException("文档不存在: " + documentId);
        if (!doc.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权提取该文档");
        }

        // 1. 读取文档全部 chunk 内容
        var chunks = documentChunkMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getDocumentId, documentId)
                        .orderByAsc(DocumentChunk::getChunkIndex));

        if (chunks.isEmpty()) {
            log.warn("文档 {} 没有分片内容", documentId);
            return Map.of("extracted", 0, "message", "文档没有分片内容");
        }

        String fullText = chunks.stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.joining("\n\n"));

        // 截断过长文本（保留前 8000 字符给 LLM）
        String excerpt = fullText.length() > 8000 ? fullText.substring(0, 8000) + "\n...(截断)" : fullText;

        // 2. 调 LLM 提取概念
        String prompt = """
                你是一个知识图谱构建助手。从以下文档中提取核心概念及其关系。

                请返回一个 JSON 对象，格式如下：
                {
                  "concepts": [
                    {"name": "概念名", "description": "一句话描述", "category": "分类", "difficulty": 3}
                  ],
                  "relations": [
                    {"from": "概念A", "to": "概念B", "type": "PREREQUISITE_OF"},
                    {"from": "概念C", "to": "概念D", "type": "RELATED_TO"}
                  ]
                }

                规则：
                - difficulty 为 1-5 的整数，1=入门，5=专家
                - PREREQUISITE_OF 表示 from 是 to 的前置知识（先学 from 才能学 to）
                - RELATED_TO 表示两个概念相关
                - 只提取文档中明确出现的概念，不要编造
                - 至少提取 3 个概念，最多 15 个

                文档内容：
                %s
                """.formatted(excerpt);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", defaultModel);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("stream", false);
            body.put("temperature", 0.3);

            String json = CLEAN_MAPPER.writeValueAsString(body);
            String resp = syncHttp(json);
            JsonNode root = CLEAN_MAPPER.readTree(resp);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            // 提取 JSON 块（LLM 可能包裹在 ```json 中）
            String jsonBlock = content;
            int start = content.indexOf("{");
            int end = content.lastIndexOf("}");
            if (start >= 0 && end > start) {
                jsonBlock = content.substring(start, end + 1);
            }

            JsonNode extracted = CLEAN_MAPPER.readTree(jsonBlock);

            int conceptCount = 0;
            int relationCount = 0;

            // 3. 写入概念
            JsonNode conceptsArray = extracted.path("concepts");
            if (conceptsArray.isArray()) {
                for (JsonNode cn : conceptsArray) {
                    String name = cn.path("name").asText();
                    if (name.isBlank()) continue;
                    String desc = cn.path("description").asText("");
                    String cat = cn.path("category").asText("未分类");
                    int diff = cn.path("difficulty").asInt(1);
                    addConcept(name, desc, cat, Math.min(5, Math.max(1, diff)), userId);
                    linkDocument(name, documentId, userId);
                    conceptCount++;
                }
            }

            // 4. 写入关系
            JsonNode relationsArray = extracted.path("relations");
            if (relationsArray.isArray()) {
                for (JsonNode rn : relationsArray) {
                    String from = rn.path("from").asText();
                    String to = rn.path("to").asText();
                    String type = rn.path("type").asText("RELATED_TO");
                    if (from.isBlank() || to.isBlank()) continue;
                    ensureExists(from, userId);
                    ensureExists(to, userId);
                    if ("PREREQUISITE_OF".equals(type)) {
                        conceptRepo.addPrerequisite(userId, from, to);
                    } else {
                        conceptRepo.addRelated(userId, from, to);
                    }
                    relationCount++;
                }
            }

            log.info("文档 {} 概念提取完成: {} 个概念, {} 条关系", doc.getFilename(), conceptCount, relationCount);
            return Map.of("extracted", conceptCount + relationCount,
                    "concepts", conceptCount,
                    "relations", relationCount,
                    "message", "已从「" + doc.getFilename() + "」提取 " + conceptCount + " 个概念、" + relationCount + " 条关系");

        } catch (Exception e) {
            log.error("文档 {} 概念提取失败", doc.getFilename(), e);
            throw new RuntimeException("概念提取失败: " + e.getMessage());
        }
    }

    private String syncHttp(String body) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.error("API 返回 {}: {}", resp.statusCode(), resp.body());
                throw new RuntimeException("API " + resp.statusCode());
            }
            return resp.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("同步请求失败", e);
            throw new RuntimeException("AI 调用失败: " + e.getMessage());
        }
    }

    public record GraphData(List<GraphNode> nodes, List<GraphEdge> edges) {
    }

    // ==================== AI 文档概念提取 ====================

    public record GraphNode(String id, String category, int difficulty, boolean center) {
    }

    // ==================== 内部 record ====================

    public record GraphEdge(String from, String to, String label) {
    }

    public record ConceptDetail(String name, String description, String category, int difficulty,
                                List<ConceptRef> prerequisites, List<ConceptRef> related, List<DocRef> documents) {
    }

    public record ConceptRef(String name, String relation, int difficulty) {
    }

    public record DocRef(Long documentId, String filename) {
    }

    public record LearningPath(List<List<PathNode>> paths) {
        public boolean isEmpty() {
            return paths == null || paths.isEmpty();
        }
    }

    public record PathNode(String name, int difficulty) {
    }
}
