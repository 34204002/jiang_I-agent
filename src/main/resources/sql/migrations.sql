-- =============================================================================
-- Jiang I-Agent — 存量数据库迁移（按时间顺序，从上往下执行）
-- =============================================================================
-- 用法：
--   全新安装：mysql -u root < schema.sql          （已含全部最新结构，不要跑本文件）
--   存量升级：mysql -u root jiang_i_agent < migrations.sql
--
-- 说明：
--   本项目历史上迁移是分散的小文件，2026-09-10 合并为本文件，段间以来源文件名标注。
--   **已执行过旧文件的库不要重跑**——多数段不是幂等的（MySQL 8 的 ADD COLUMN 无
--   IF NOT EXISTS），重跑会报 "Duplicate column name"，属预期，跳过即可。
--   各段自身的幂等说明保留在段内注释里。
-- =============================================================================


-- =============================================================================
-- 【1/4】来源：migration_update_model.sql
-- =============================================================================
-- 修正 t_agent_config 默认模型
-- 旧 schema 默认 'deepseek-ai/DeepSeek-V3.2' 与 application-dev.yml 的
-- 'deepseek-v4-flash' 不一致，导致 ChatService.getModel() 优先读 DB 时
-- 实际使用 V3.2。此迁移把既有配置行统一为 v4-flash（幂等，可重复执行）。
-- -----------------------------------------------------------------------------

UPDATE t_agent_config
SET model = 'deepseek-v4-flash'
WHERE id = 1
  AND model IN ('', 'deepseek-ai/DeepSeek-V3.2', 'DeepSeek-V3.2');


-- =============================================================================
-- 【2/4】来源：migration_add_user_isolation.sql
-- =============================================================================
-- 知识库/图谱用户隔离迁移
-- 适用：从"全局共享"升级到"按用户隔离"的存量数据库。
-- -----------------------------------------------------------------------------

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
--   MATCH (c:Concept) WHERE c.userId IS NULL
--   SET c.userId = <OLDEST_USER_ID>
--   RETURN count(c) AS migrated;
--
-- 注意：必须用 `WHERE c.userId IS NULL`。`WHERE NOT EXISTS(c.userId)` 是
--       Neo4j 5 已废弃语法（见 md/ISSUES.md #31），旧写法在本项目实测不可用。
-- 幂等：只处理没有 userId 属性的节点。


-- =============================================================================
-- 【3/4】来源：migration_add_user_llm_key.sql
-- =============================================================================
-- 用户 BYOK 迁移：t_user 增加 api_key_enc / llm_model 两列
-- 适用：从"全局单一模型"升级到"用户自带 DeepSeek key + 自选模型"的存量数据库。
-- 全新安装直接由 schema.sql 创建，无需本段。
-- -----------------------------------------------------------------------------

ALTER TABLE t_user
    ADD COLUMN api_key_enc VARCHAR(500) NULL COMMENT '用户自填 DeepSeek API Key（AES-GCM 密文）' AFTER avatar,
    ADD COLUMN llm_model   VARCHAR(50)  NULL COMMENT '用户自选对话模型名' AFTER api_key_enc;


-- =============================================================================
-- 【4/4】来源：migration_knowledge_async.sql
-- =============================================================================
-- RabbitMQ 文档异步处理：t_document 增加失败原因列（status=3 时前端可见）
-- 幂等：若已存在该列（重复执行）则忽略错误。
-- -----------------------------------------------------------------------------

ALTER TABLE t_document
    ADD COLUMN error_message VARCHAR(255) NULL COMMENT '处理失败原因（status=3 可见）' AFTER status;