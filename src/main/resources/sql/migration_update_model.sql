-- =============================================================================
-- 修正 t_agent_config 默认模型
-- 旧 schema 默认 'deepseek-ai/DeepSeek-V3.2' 与 application-dev.yml 的
-- 'deepseek-v4-flash' 不一致，导致 ChatService.getModel() 优先读 DB 时
-- 实际使用 V3.2。此迁移把既有配置行统一为 v4-flash（幂等，可重复执行）。
-- =============================================================================

UPDATE t_agent_config
SET model = 'deepseek-v4-flash'
WHERE id = 1
  AND model IN ('', 'deepseek-ai/DeepSeek-V3.2', 'DeepSeek-V3.2');
