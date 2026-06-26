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
 */
@Repository
public interface ConceptRepository extends Neo4jRepository<ConceptEntity, String> {

    /** 按名称精确查找 */
    Optional<ConceptEntity> findByName(String name);

    /** 按分类查找 */
    List<ConceptEntity> findByCategory(String category);

    /** 模糊搜索概念（regex 匹配，支持任意字符间隔） */
    @Query("MATCH (c:Concept) WHERE toLower(c.name) =~ toLower($regex) "
         + "RETURN c ORDER BY c.name SKIP $skip LIMIT $limit")
    List<ConceptEntity> searchByName(@Param("regex") String regex,
                                     @Param("skip") int skip,
                                     @Param("limit") int limit);

    /** 模糊搜索总数 */
    @Query("MATCH (c:Concept) WHERE toLower(c.name) =~ toLower($regex) RETURN count(c)")
    int countByName(@Param("regex") String regex);

    /** 精确包含搜索（前端搜索框用 CONTAINS） */
    @Query("MATCH (c:Concept) WHERE toLower(c.name) CONTAINS toLower($keyword) "
         + "RETURN c ORDER BY c.name SKIP $skip LIMIT $limit")
    List<ConceptEntity> searchByNameContains(@Param("keyword") String keyword,
                                              @Param("skip") int skip,
                                              @Param("limit") int limit);

    /** 精确包含搜索总数 */
    @Query("MATCH (c:Concept) WHERE toLower(c.name) CONTAINS toLower($keyword) RETURN count(c)")
    int countByNameContains(@Param("keyword") String keyword);

    /** 按分类搜索 */
    @Query("MATCH (c:Concept) WHERE c.category = $category "
         + "RETURN c ORDER BY c.name SKIP $skip LIMIT $limit")
    List<ConceptEntity> findByCategoryPaged(@Param("category") String category,
                                            @Param("skip") int skip,
                                            @Param("limit") int limit);

    /** 按分类统计 */
    @Query("MATCH (c:Concept) WHERE c.category = $category RETURN count(c)")
    int countByCategory(@Param("category") String category);

    /** 全量分页 */
    @Query("MATCH (c:Concept) RETURN c ORDER BY c.name SKIP $skip LIMIT $limit")
    List<ConceptEntity> findAllPaged(@Param("skip") int skip, @Param("limit") int limit);

    /**
     * 查询概念的所有前置知识（单跳）。
     */
    @Query("MATCH (c:Concept {name: $name})-[:PREREQUISITE_OF]->(p:Concept) RETURN p")
    List<ConceptEntity> findDirectPrerequisites(@Param("name") String name);

    /**
     * 查询概念的所有相关概念（单跳）。
     */
    @Query("MATCH (c:Concept {name: $name})-[:RELATED_TO]->(r:Concept) RETURN r")
    List<ConceptEntity> findDirectRelated(@Param("name") String name);

    /**
     * 添加前置知识关系。
     */
    @Query("MATCH (a:Concept {name: $from}), (b:Concept {name: $to}) "
         + "MERGE (a)-[:PREREQUISITE_OF]->(b)")
    void addPrerequisite(@Param("from") String from, @Param("to") String to);

    /**
     * 添加相关关系。
     */
    @Query("MATCH (a:Concept {name: $from}), (b:Concept {name: $to}) "
         + "MERGE (a)-[:RELATED_TO]->(b)")
    void addRelated(@Param("from") String from, @Param("to") String to);

    /**
     * 关联概念到文档。
     */
    @Query("MATCH (c:Concept {name: $conceptName}) "
         + "SET c.documentIds = CASE WHEN $docId IN c.documentIds THEN c.documentIds "
         + "ELSE c.documentIds + $docId END")
    void linkDocument(@Param("conceptName") String conceptName, @Param("docId") Long docId);
}
