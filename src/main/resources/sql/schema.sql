-- =============================================================================
-- Jiang I-Agent — MySQL DDL
-- 数据库: jiang_i_agent
-- 按 Phase 分组建表，当前 Phase 1 不建表，Phase 2 起逐步启用
-- =============================================================================

CREATE DATABASE IF NOT EXISTS jiang_i_agent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE jiang_i_agent;

-- =============================================================================
-- Phase 2 — RAG 知识库
-- =============================================================================

-- 文档表：记录上传的知识库文档元数据
CREATE TABLE IF NOT EXISTS t_document (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '文档 ID',
    filename     VARCHAR(255)    NOT NULL                  COMMENT '原始文件名',
    file_type    VARCHAR(20)     NOT NULL                  COMMENT '文件类型：md/pdf/txt/docx',
    file_size    BIGINT UNSIGNED NOT NULL DEFAULT 0         COMMENT '文件大小（字节）',
    content_hash VARCHAR(64)     NOT NULL DEFAULT ''        COMMENT 'SHA-256 内容哈希，用于去重',
    chunk_count  INT UNSIGNED    NOT NULL DEFAULT 0         COMMENT '分片数量',
    status       TINYINT         NOT NULL DEFAULT 0         COMMENT '处理状态：0-待处理 1-已解析 2-已向量化',
    summary      VARCHAR(500)    NOT NULL DEFAULT ''        COMMENT 'LLM 生成的文档摘要',
    uploaded_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    UNIQUE KEY uk_content_hash (content_hash),
    INDEX        idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库文档';

-- 文档分片表：文档切分后的文本块
CREATE TABLE IF NOT EXISTS t_document_chunk (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '分片 ID',
    document_id  BIGINT UNSIGNED NOT NULL                   COMMENT '所属文档 ID',
    chunk_index  INT UNSIGNED    NOT NULL                   COMMENT '分片序号（从 0 开始）',
    content      TEXT            NOT NULL                   COMMENT '分片文本内容',
    token_count  INT UNSIGNED    NOT NULL DEFAULT 0         COMMENT 'Token 数量估算',
    embedding_id VARCHAR(100)    NOT NULL DEFAULT ''        COMMENT 'Qdrant 中对应的 Point ID',
    UNIQUE KEY   uk_doc_chunk (document_id, chunk_index),
    FOREIGN KEY  (document_id) REFERENCES t_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档分片';

-- =============================================================================
-- Phase 4 — 对话持久化 + 待办 + 日志
-- =============================================================================

-- 会话表
CREATE TABLE IF NOT EXISTS t_conversation (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '会话 ID',
    title         VARCHAR(200)    NOT NULL DEFAULT ''        COMMENT '会话标题（首条消息摘要）',
    model         VARCHAR(50)     NOT NULL DEFAULT ''        COMMENT '使用的模型',
    message_count INT UNSIGNED    NOT NULL DEFAULT 0         COMMENT '消息总数',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX         idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对话会话';

-- 消息表
CREATE TABLE IF NOT EXISTS t_message (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '消息 ID',
    conversation_id BIGINT UNSIGNED NOT NULL                 COMMENT '所属会话 ID',
    role            VARCHAR(20)     NOT NULL                 COMMENT '角色：user / assistant / tool',
    content         MEDIUMTEXT      NULL                     COMMENT '消息正文',
    tool_calls      JSON            NULL                     COMMENT '工具调用记录 [{"name":"","input":{},"output":""}]',
    token_count     INT UNSIGNED    NOT NULL DEFAULT 0       COMMENT 'Token 消耗估算',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX           idx_conv_msg (conversation_id, created_at),
    FOREIGN KEY     (conversation_id) REFERENCES t_conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对话消息';

-- 待办表
CREATE TABLE IF NOT EXISTS t_todo_item (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '待办 ID',
    conversation_id BIGINT UNSIGNED NULL                     COMMENT '关联会话 ID',
    title           VARCHAR(500)    NOT NULL                 COMMENT '待办标题',
    due_date        DATE            NULL                     COMMENT '截止日期',
    is_done         TINYINT         NOT NULL DEFAULT 0       COMMENT '0-未完成 1-已完成',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME        NULL,
    INDEX           idx_done (is_done, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='待办事项';

-- 工具调用日志表（面试亮点：可观测性）
CREATE TABLE IF NOT EXISTS t_tool_usage_log (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '日志 ID',
    conversation_id BIGINT UNSIGNED NOT NULL,
    message_id      BIGINT UNSIGNED NOT NULL,
    tool_name       VARCHAR(50)     NOT NULL                 COMMENT '工具名称',
    input_json      JSON            NULL                     COMMENT '工具入参',
    output_text     TEXT            NULL                     COMMENT '工具返回',
    duration_ms     INT UNSIGNED    NOT NULL DEFAULT 0       COMMENT '执行耗时（毫秒）',
    success         TINYINT         NOT NULL DEFAULT 1       COMMENT '0-失败 1-成功',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX           idx_tool_time (tool_name, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工具调用日志';
