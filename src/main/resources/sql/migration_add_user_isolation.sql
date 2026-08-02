-- =============================================================================
-- 知识库/图谱用户隔离迁移
-- 适用：从"全局共享"升级到"按用户隔离"的存量数据库。
-- =============================================================================

-- ---------- MySQL：知识库文档 ----------

-- 1. 加 user_id 列（先允许 0，迁移后再约束）
ALTER TABLE t_document
    ADD COLUMN user_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属用户' AFTER id;

-- 2. 存量文档归给最早注册的用户（user_id 最小者）
UPDATE t_document d
    JOIN (SELECT id FROM t_user ORDER BY id ASC LIMIT 1) u
    SET d.user_id = u.id
WHERE d.user_id = 0;

-- 3. 去重键改为按用户唯一（同一用户不能重复上传同一内容，不同用户可以各自拥有）
ALTER TABLE t_document DROP INDEX uk_content_hash;
ALTER TABLE t_document ADD UNIQUE KEY uk_user_hash (user_id, content_hash);

-- 4. 加用户索引
CREATE INDEX idx_user ON t_document (user_id);

-- ---------- Neo4j：知识图谱概念 ----------
-- 在 Neo4j 浏览器或 cypher-shell 执行以下语句（<OLDEST_USER_ID> 替换为最早用户 id）：
--
--   MATCH (c:Concept) WHERE NOT EXISTS(c.userId)
--   SET c.userId = <OLDEST_USER_ID>
--   RETURN count(c) AS migrated;
--
-- 幂等：只处理没有 userId 属性的节点。
