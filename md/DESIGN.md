# Jiang I-Agent — 技术设计文档

> 版本选型决策 + 数据库设计 + Neo4j 图谱模型 + Redis 键设计 + 向量库选型

---

## 一、版本选型决策

| 决策 | 选项 | 原因 |
|------|------|------|
| Spring Boot 版本 | **4.1.0** | 阿里云 Maven 镜像全量支持，Java 21 + Virtual Threads |
| Spring AI 版本 | **2.0.0** | 使用 `spring-ai-deepseek` 专用 API（ChatCompletion/Chunk 带 reasoningContent） |
| 大模型接入 | **DeepSeek 官方 API** | 直接 `api.deepseek.com`，HttpClient 构建请求 + Spring AI 类型解析 |
| 嵌入模型 | **BAAI/bge-m3** | 硅基流动提供，1024 维向量（text-embedding-3-small 是 OpenAI 模型，硅基不支持） |
| 序列化 | **Jackson** | API 请求/响应序列化，工具调用参数解析 |

---

## 二、MySQL 表设计

全部表使用 InnoDB + utf8mb4，主键统一 `BIGINT UNSIGNED AUTO_INCREMENT`。

### 2.1 对话表

```sql
CREATE TABLE t_conversation (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(200)    NOT NULL DEFAULT '',
    model         VARCHAR(50)     NOT NULL DEFAULT '',
    message_count INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_created (created_at)
);

CREATE TABLE t_message (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT UNSIGNED NOT NULL,
    role            VARCHAR(20)     NOT NULL,              -- user / assistant / tool
    content         MEDIUMTEXT      NULL,
    tool_calls      JSON            NULL,                  -- [{name, input, output}]
    token_count     INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_msg (conversation_id, created_at),
    FOREIGN KEY (conversation_id) REFERENCES t_conversation(id) ON DELETE CASCADE
);
```

### 2.2 文档表（RAG）

```sql
CREATE TABLE t_document (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    filename     VARCHAR(255)    NOT NULL,
    file_type    VARCHAR(20)     NOT NULL,                  -- md / pdf / txt
    file_size    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    content_hash VARCHAR(64)     NOT NULL DEFAULT '',       -- SHA-256 去重
    chunk_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    status       TINYINT         NOT NULL DEFAULT 0,        -- 0-待处理 1-已解析 2-已向量化
    summary      VARCHAR(500)    NOT NULL DEFAULT '',
    uploaded_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_hash (content_hash),
    INDEX idx_status (status)
);

CREATE TABLE t_document_chunk (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    document_id  BIGINT UNSIGNED NOT NULL,
    chunk_index  INT UNSIGNED    NOT NULL,
    content      TEXT            NOT NULL,
    token_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    embedding_id VARCHAR(100)    NOT NULL DEFAULT '',       -- Qdrant point ID
    UNIQUE KEY uk_doc_chunk (document_id, chunk_index),
    FOREIGN KEY (document_id) REFERENCES t_document(id) ON DELETE CASCADE
);
```

### 2.3 待办表

```sql
CREATE TABLE t_todo_item (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT UNSIGNED NULL,
    title           VARCHAR(500)    NOT NULL,
    due_date        DATE            NULL,
    is_done         TINYINT         NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_done (is_done, due_date)
);
```

### 2.4 工具调用日志（可观测性）

```sql
CREATE TABLE t_tool_usage_log (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT UNSIGNED NOT NULL,
    message_id      BIGINT UNSIGNED NOT NULL,
    tool_name       VARCHAR(50)     NOT NULL,
    input_json      JSON            NULL,
    output_text     TEXT            NULL,
    duration_ms     INT UNSIGNED    NOT NULL DEFAULT 0,
    success         TINYINT         NOT NULL DEFAULT 1,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tool_time (tool_name, created_at)
);
```

### 索引设计要点

| 索引 | 原因 |
|------|------|
| `idx_conv_msg(conversation_id, created_at)` | 按会话查消息是最高频查询，等值在前、排序在后 |
| `uk_hash(content_hash)` | SHA-256 唯一，防止重复上传同一文件 |
| `idx_done(is_done, due_date)` | 查未完成待办按截止日期排序 |
| `idx_tool_time(tool_name, created_at)` | 按工具名统计调用量 |

---

## 三、Neo4j 图谱模型

### 节点标签

```cypher
-- 知识概念
(:Concept {
  name: "Redis",
  description: "内存键值数据库，支持持久化、主从、哨兵、集群",
  category: "中间件",
  difficulty: 3
})

-- 文档引用（对应 MySQL t_document.id）
(:Document { documentId: 1, title: "Redis实战笔记.md", type: "md" })

-- 分类
(:Category { name: "中间件" })
```

### 关系

| 关系 | 方向 | 含义 |
|------|------|------|
| `PREREQUISITE_OF` | (A)→(B) | A 是 B 的前置知识 |
| `RELATED_TO` | (A)→(B) | 相关知识 |
| `MENTIONED_IN` | (Concept)→(Document) | 概念在某文档中被提及 |
| `BELONGS_TO` | (Concept)→(Category) | 分类归属 |

### 核心查询

```cypher
-- 知识链：Redis 的 1~3 跳前置知识（向量检索做不到）
MATCH path = (c:Concept {name: "Redis"})-[:PREREQUISITE_OF*1..3]->(target)
RETURN path

-- 某概念关联的所有文档
MATCH (c:Concept {name: "Redis"})-[:MENTIONED_IN]->(d:Document)
RETURN c, d
```

---

## 四、Qdrant 向量库

### Collection 设计

| 参数 | 值 |
|------|-----|
| Collection 名 | `jiang_i_agent_knowledge` |
| 向量维度 | 1024（BAAI/bge-m3） |
| 距离算法 | Cosine |

### Payload

```json
{
  "document_id": 1,
  "chunk_index": 3,
  "content": "Redis 持久化主要有 RDB 和 AOF 两种方式...",
  "filename": "Redis实战笔记.md"
}
```

### Spring AI 配置

```yaml
spring.ai.vectorstore.qdrant:
  host: localhost
  port: 6334
  initialize-schema: true       # 首次启动自动建 Collection
```

---

## 五、Redis 键设计

| Key 模式 | 类型 | TTL | 用途 |
|----------|------|-----|------|
| `agent:chat:memory:{convId}` | List | 30min | ChatMemory 对话历史（官方 Starter 自动管理） |
| `agent:rate_limit:{key}:{min}` | String | 60s | 令牌桶限流计数器 |
| `agent:session:{sessionId}` | Hash | 30min | 会话元数据 |
| `agent:embedding_cache:{hash}` | String | 24h | 向量化结果缓存（节省 API 费用） |

---

## 六、分阶段建表策略

| 阶段 | 建表 | Qdrant | Neo4j |
|------|------|--------|-------|
| P1（框架） | 不建表 | 不操作 | 不操作 |
| P2（RAG） | `t_document` + `t_document_chunk` | 建 Collection + 写入向量 | 不操作 |
| P3（图谱） | 不新增 | 只读 | 建节点+关系 |
| P4（工程化） | `t_conversation` + `t_message` + `t_todo_item` + `t_tool_usage_log` | 读写 | 读写 |
