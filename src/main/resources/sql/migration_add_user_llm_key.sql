-- =============================================================================
-- 用户 BYOK 迁移：t_user 增加 api_key_enc / llm_model 两列
-- 适用：从"全局单一模型"升级到"用户自带 DeepSeek key + 自选模型"的存量数据库。
-- 全新安装直接由 schema.sql 创建，无需本文件。
-- =============================================================================

ALTER TABLE t_user
    ADD COLUMN api_key_enc VARCHAR(500) NULL COMMENT '用户自填 DeepSeek API Key（AES-GCM 密文）' AFTER avatar,
    ADD COLUMN llm_model   VARCHAR(50)  NULL COMMENT '用户自选对话模型名' AFTER api_key_enc;