package com.jiang.repository;

import com.jiang.entity.ConceptEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识概念 Neo4j Repository — 封装概念 CRUD 和 Cypher 路径查询。
 * <p>
 * 用户隔离：概念按 (name, userId) 归属，所有读写都带 userId 过滤。
 * 不同用户的同名概念是 Neo4j 中不同的节点（MERGE 以 name+userId 为唯一键）。
 * 注意：不再使用基于 name@Id 的 findById/save/deleteById/count，改走自定义查询。
 */
@Repository
public interface ConceptRepository extends Neo4jRepository<ConceptEntity, String> {

    /**
     * 按名称 + 用户精确查找
     */
    @Query("MATCH (c:Concept {name: $name, userId: $userId}) RETURN c")
    Optional<ConceptEntity> findByNameAndUserId(@Param("name") String name, @Param("userId") Long userId);

    /**
     * 创建或更新概念（MERGE 以 name+userId 为唯一键，实现用户隔离）
     */
    @Query("MERGE (c:Concept {name: $name, userId: $userId}) "
            + "SET c.description = $description, c.category = $category, c.difficulty = $difficulty RETURN c")
    ConceptEntity upsert(@Param("name") String name, @Param("userId") Long userId,
                         @Param("description") String description, @Param("category") String category,
                         @Param("difficulty") int difficulty);

    /**
     * 删除概念及所有关系（用户隔离）
     */
    @Query("MATCH (c:Concept {name: $name, userId: $userId}) DETACH DELETE c")
    void deleteByNameAndUserId(@Param("name") String name, @Param("userId") Long userId);

    /**
     * 按用户统计概念总数
     */
    @Query("MATCH (c:Concept) WHERE c.userId = $userId RETURN count(c)")
    long countByUserId(@Param("userId") Long userId);

    /**
     * 模糊搜索概念（regex 匹配，用户隔离）
     */
    @Query("MATCH (c:Concept) WHERE c.userId = $userId AND toLower(c.name) =~ toLower($regex) "
            + "RETURN c ORDER BY c.name SKIP $skip LIMIT $limit")
    List<ConceptEntity> searchByName(@Param("userId") Long userId, @Param("regex") String regex,
                                     @Param("skip") int skip, @Param("limit") int limit);

    /**
     * 模糊搜索总数
     */
    @Query("MATCH (c:Concept) WHERE c.userId = $userId AND toLower(c.name) =~ toLower($regex) RETURN count(c)")
    int countByName(@Param("userId") Long userId, @Param("regex") String regex);

    /**
     * 精确包含搜索（前端搜索框用 CONTAINS）
     */
    @Query("MATCH (c:Concept) WHERE c.userId = $userId AND toLower(c.name) CONTAINS toLower($keyword) "
            + "RETURN c ORDER BY c.name SKIP $skip LIMIT $limit")
    List<ConceptEntity> searchByNameContains(@Param("userId") Long userId, @Param("keyword") String keyword,
                                             @Param("skip") int skip, @Param("limit") int limit);

    /**
     * 精确包含搜索总数
     */
    @Query("MATCH (c:Concept) WHERE c.userId = $userId AND toLower(c.name) CONTAINS toLower($keyword) RETURN count(c)")
    int countByNameContains(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 按分类搜索
     */
    @Query("MATCH (c:Concept) WHERE c.userId = $userId AND c.category = $category "
            + "RETURN c ORDER BY c.name SKIP $skip LIMIT $limit")
    List<ConceptEntity> findByCategoryPaged(@Param("userId") Long userId, @Param("category") String category,
                                            @Param("skip") int skip, @Param("limit") int limit);

    /**
     * 按分类统计
     */
    @Query("MATCH (c:Concept) WHERE c.userId = $userId AND c.category = $category RETURN count(c)")
    int countByCategory(@Param("userId") Long userId, @Param("category") String category);

    /**
     * 全量分页
     */
    @Query("MATCH (c:Concept) WHERE c.userId = $userId RETURN c ORDER BY c.name SKIP $skip LIMIT $limit")
    List<ConceptEntity> findAllPaged(@Param("userId") Long userId, @Param("skip") int skip, @Param("limit") int limit);

    /**
     * 查询概念的所有前置知识（单跳，用户隔离）。
     */
    @Query("MATCH (c:Concept {name: $name, userId: $userId})-[:PREREQUISITE_OF]->(p:Concept) RETURN p")
    List<ConceptEntity> findDirectPrerequisites(@Param("userId") Long userId, @Param("name") String name);

    /**
     * 查询概念的所有相关概念（单跳，用户隔离）。
     */
    @Query("MATCH (c:Concept {name: $name, userId: $userId})-[:RELATED_TO]->(r:Concept) RETURN r")
    List<ConceptEntity> findDirectRelated(@Param("userId") Long userId, @Param("name") String name);

    /**
     * 添加前置知识关系（用户隔离）。
     */
    @Query("MATCH (a:Concept {name: $from, userId: $userId}), (b:Concept {name: $to, userId: $userId}) "
            + "MERGE (a)-[:PREREQUISITE_OF]->(b)")
    void addPrerequisite(@Param("userId") Long userId, @Param("from") String from, @Param("to") String to);

    /**
     * 添加相关关系（用户隔离）。
     */
    @Query("MATCH (a:Concept {name: $from, userId: $userId}), (b:Concept {name: $to, userId: $userId}) "
            + "MERGE (a)-[:RELATED_TO]->(b)")
    void addRelated(@Param("userId") Long userId, @Param("from") String from, @Param("to") String to);

    /**
     * 关联概念到文档（用户隔离）。
     */
    @Query("MATCH (c:Concept {name: $conceptName, userId: $userId}) "
            + "SET c.documentIds = CASE WHEN $docId IN c.documentIds THEN c.documentIds "
            + "ELSE c.documentIds + $docId END")
    void linkDocument(@Param("userId") Long userId, @Param("conceptName") String conceptName,
                      @Param("docId") Long docId);

    /**
     * 删除两个概念之间的指定类型关系（用户隔离）
     */
    @Query("MATCH (a:Concept {name: $from, userId: $userId})-[r]->(b:Concept {name: $to, userId: $userId}) "
            + "WHERE type(r) = $type DELETE r")
    void deleteRelation(@Param("userId") Long userId, @Param("from") String from, @Param("to") String to,
                        @Param("type") String type);

    /**
     * 检查两个概念之间是否存在某种关系（用户隔离）
     */
    @Query("MATCH (a:Concept {name: $from, userId: $userId})-[r]->(b:Concept {name: $to, userId: $userId}) "
            + "WHERE type(r) = $type RETURN count(r) > 0")
    boolean hasRelation(@Param("userId") Long userId, @Param("from") String from, @Param("to") String to,
                        @Param("type") String type);

    /**
     * 查询某用户所有概念的分类（去重），供前端筛选下拉
     */
    @Query("MATCH (c:Concept) WHERE c.userId = $userId RETURN DISTINCT c.category")
    List<String> findAllCategories(@Param("userId") Long userId);
}
