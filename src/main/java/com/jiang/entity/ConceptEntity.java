package com.jiang.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j 知识概念节点。
 * <p>
 * 关系：
 * <ul>
 *   <li>{@code PREREQUISITE_OF} — A 是 B 的前置知识</li>
 *   <li>{@code RELATED_TO} — 相关概念</li>
 *   <li>{@code MENTIONED_IN} — 概念在某文档中被提及</li>
 *   <li>{@code BELONGS_TO} — 分类归属</li>
 * </ul>
 */
@Node("Concept")
public class ConceptEntity {

    @Id
    private String name;

    private String description;

    private String category;

    private Integer difficulty; // 1-5

    /**
     * 前置知识（指向自己的一方是"被需要"的一方）
     */
    @Relationship(type = "PREREQUISITE_OF", direction = Relationship.Direction.OUTGOING)
    private List<ConceptEntity> prerequisites = new ArrayList<>();

    /**
     * 相关概念
     */
    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private List<ConceptEntity> related = new ArrayList<>();

    /**
     * 关联文档 ID 列表（不存为独立节点，用属性引用）
     */
    private List<Long> documentIds = new ArrayList<>();

    // ==================== constructors ====================

    public ConceptEntity() {
    }

    public ConceptEntity(String name, String description, String category, Integer difficulty) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.difficulty = difficulty;
    }

    // ==================== getters / setters ====================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public List<ConceptEntity> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(List<ConceptEntity> prerequisites) {
        this.prerequisites = prerequisites;
    }

    public List<ConceptEntity> getRelated() {
        return related;
    }

    public void setRelated(List<ConceptEntity> related) {
        this.related = related;
    }

    public List<Long> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<Long> documentIds) {
        this.documentIds = documentIds;
    }
}
