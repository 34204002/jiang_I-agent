-- =============================================================================
-- Jiang I-Agent — MySQL DDL（全新建库用，会覆盖已有表）
-- =============================================================================

DROP DATABASE IF EXISTS jiang_i_agent;
CREATE DATABASE jiang_i_agent
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE jiang_i_agent;

-- =============================================================================
-- 用户认证
-- =============================================================================

CREATE TABLE t_user
(
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '用户主键',
    username   VARCHAR(50)  NOT NULL COMMENT '用户名',
    password   VARCHAR(200) NOT NULL COMMENT 'BCrypt 密文',
    nickname   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '昵称',
    avatar       VARCHAR(500) NOT NULL DEFAULT '' COMMENT '头像 URL（OSS）',
    role         VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'ADMIN / USER',
    api_key_enc  VARCHAR(500) NULL COMMENT '用户自填 DeepSeek API Key（AES-GCM 密文，BYOK）',
    llm_model    VARCHAR(50)  NULL COMMENT '用户自选对话模型名（可为空=全局模型）',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户';

-- 创建管理员: 先通过 /login.html 注册 admin，再执行:
-- UPDATE t_user SET role = 'ADMIN' WHERE username = 'admin';

-- =============================================================================
-- Agent 全局配置
-- =============================================================================

CREATE TABLE t_agent_config
(
    id            INT PRIMARY KEY COMMENT '固定为 1',
    agent_name    VARCHAR(100)  NOT NULL DEFAULT 'Jiang I-Agent' COMMENT 'Agent 名称',
    avatar        VARCHAR(500)  NOT NULL DEFAULT '' COMMENT 'Agent 头像 URL',
    system_prompt TEXT          NULL COMMENT '系统提示词',
    model         VARCHAR(50)   NOT NULL DEFAULT 'deepseek-v4-flash' COMMENT '默认模型',
    temperature   DECIMAL(3, 2) NOT NULL DEFAULT 0.7 COMMENT '温度参数',
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='Agent 全局配置';

INSERT INTO t_agent_config (id, agent_name)
VALUES (1, 'Jiang I-Agent');

-- =============================================================================
-- 对话会话
-- =============================================================================

CREATE TABLE t_conversation
(
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '会话 ID',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    title         VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '会话标题',
    model         VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '模型',
    message_count INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '消息数',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_created (created_at),
    FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='对话会话';

-- =============================================================================
-- 对话消息
-- =============================================================================

CREATE TABLE t_message
(
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '消息 ID',
    conversation_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话',
    role            VARCHAR(20)     NOT NULL COMMENT 'user / assistant / tool',
    content         MEDIUMTEXT      NULL COMMENT '消息正文',
    thinking        MEDIUMTEXT      NULL COMMENT '思考内容（DeepSeek reasoning_content）',
    tool_calls      JSON            NULL COMMENT '工具调用 JSON',
    token_count     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Token 消耗',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_msg (conversation_id, created_at),
    FOREIGN KEY (conversation_id) REFERENCES t_conversation (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='对话消息';

-- =============================================================================
-- RAG 知识库
-- =============================================================================

CREATE TABLE t_document
(
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '文档 ID',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    filename     VARCHAR(255)    NOT NULL COMMENT '原始文件名',
    file_type    VARCHAR(20)     NOT NULL COMMENT 'md / pdf / txt / docx',
    file_size    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    content_hash VARCHAR(64)     NOT NULL DEFAULT '' COMMENT 'SHA-256 去重（按用户）',
    chunk_count  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '分片数',
    status       TINYINT         NOT NULL DEFAULT 0 COMMENT '0-待处理 1-已解析 2-已向量化',
    summary      VARCHAR(500)    NOT NULL DEFAULT '' COMMENT 'LLM 摘要',
    oss_key      VARCHAR(200)    NOT NULL DEFAULT '' COMMENT 'OSS 存储 key',
    uploaded_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_hash (user_id, content_hash),
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识库文档';

CREATE TABLE t_document_chunk
(
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '分片 ID',
    document_id  BIGINT UNSIGNED NOT NULL COMMENT '所属文档',
    chunk_index  INT UNSIGNED    NOT NULL COMMENT '分片序号',
    content      TEXT            NOT NULL COMMENT '分片文本',
    token_count  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Token 数',
    embedding_id VARCHAR(100)    NOT NULL DEFAULT '' COMMENT 'Qdrant Point ID',
    UNIQUE KEY uk_doc_chunk (document_id, chunk_index),
    FOREIGN KEY (document_id) REFERENCES t_document (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文档分片';

-- =============================================================================
-- 待办事项
-- =============================================================================

CREATE TABLE t_todo_item
(
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '待办 ID',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    conversation_id BIGINT UNSIGNED NULL COMMENT '关联会话',
    title           VARCHAR(500)    NOT NULL COMMENT '标题',
    due_date        DATE            NULL COMMENT '截止日期',
    is_done         TINYINT         NOT NULL DEFAULT 0 COMMENT '0-未完成 1-已完成',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME        NULL,
    INDEX idx_done (is_done, due_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='待办事项';

-- =============================================================================
-- 定时提醒
-- =============================================================================

CREATE TABLE IF NOT EXISTS t_reminder
(
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '提醒 ID',
    user_id    BIGINT UNSIGNED NOT NULL,
    message    VARCHAR(500)    NOT NULL COMMENT '提醒内容',
    remind_at  DATETIME        NOT NULL COMMENT '提醒时间',
    fired      TINYINT         NOT NULL DEFAULT 0 COMMENT '0-未触发 1-已触发',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_remind (user_id, remind_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='定时提醒';
