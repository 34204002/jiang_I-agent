# Jiang I-Agent — 技术设计文档

> 版本选型决策 + 数据库设计 + Neo4j 图谱模型 + Redis 键设计 + 向量库选型 + 前端架构

---

## 一、版本选型决策

| 决策 | 选项 | 原因 |
|------|------|------|
| Spring Boot 版本 | **4.1.0** | Java 21 + Virtual Threads |
| Spring AI 版本 | **2.0.0** | `spring-ai-deepseek` 专用 API（ChatCompletionMessage.reasoningContent()） |
| 大模型接入 | **DeepSeek 官方 API** | `api.deepseek.com`，`buildRequestBody()` 构建 + Spring AI 类型解析 |
| 嵌入模型 | **BAAI/bge-m3** | 硅基流动提供，1024 维（text-embedding-3-small 是 OpenAI 模型，不支持） |
| 前端框架 | **Vue 3 + Vite** | SFC 组件 + vue-router SPA + 设计系统 CSS 自定义属性 |
| 工具框架 | **自研 @Tool** | 脱离 Spring AI ChatClient 链路后无法用官方 @Tool；自建注解扫描 + 反射执行 |
| 序列化 | **Jackson** | API 请求/响应序列化，工具调用参数解析 |

---

## 一-补充：为什么自建 @Tool 框架

项目**用了 `spring-ai-deepseek` 模块，但没有走标准 ChatClient 链路**。当前架构：

```
HTTP 请求层: 自建 buildRequestBody() + streamHttp/syncHttp（Map → JSON → HttpClient）
    ↓
响应解析层: DeepSeekApi.ChatCompletionChunk / ChatCompletionMessage（Spring AI 类型，类型安全）
    ↓
工具编排层: 自建 ToolRegistry + @Tool 注解（脱离 ChatClient 后官方 @Tool 不可用）
```

为什么不用 Spring AI 全套（ChatClient → ChatModel → ToolCallback）？

1. **最初用 OpenAI 适配器** → 底层 OpenAI Java SDK 不认识 `reasoning_content` → 流式时丢弃
2. **切到 spring-ai-deepseek** → `ChatCompletionMessage.reasoningContent()` 可用，但若走 `ChatClient.stream()` 链路，工具调用行为（并行 tool_calls、DSML fallback）不够透明可控
3. **最终决策**：保留 `spring-ai-deepseek` 做**类型解析**（`ChatCompletionChunk`/`ChatCompletionMessage`），但 HTTP 请求和工具编排**自己控制**——这样既有类型安全，又能精细处理 DeepSeek 的特殊行为

结果：`spring-ai-deepseek` 模块在 pom.xml 中且被引用，但只用于响应反序列化，不走它的 `ChatClient`/`@Tool` 体系。

```
自建 @Tool 注解
  → ApplicationReadyEvent 扫描所有 Bean 的 @Tool 方法
  → 注册到 ToolRegistry (name → Method + bean + JSON Schema)
  → ChatService 构建 tools 参数时调用 toolRegistry.getToolsJson()
  → 执行时 toolRegistry.execute(name, args) 反射调用
  → ToolContext (ThreadLocal) 传递 userId/convoId/reasoningContent
```

---

## 二、MySQL 表设计

全部表使用 InnoDB + utf8mb4，主键统一 `BIGINT UNSIGNED AUTO_INCREMENT`。

### 2.1 用户认证

```sql
CREATE TABLE t_user (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)     NOT NULL,
    password      VARCHAR(200)    NOT NULL,              -- BCrypt 密文
    nickname      VARCHAR(100)    NOT NULL DEFAULT '',
    avatar        VARCHAR(500)    NOT NULL DEFAULT '',    -- 阿里云 OSS URL
    role          VARCHAR(20)     NOT NULL DEFAULT 'USER',-- ADMIN / USER
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
);

CREATE TABLE t_agent_config (
    id            INT PRIMARY KEY,                       -- 固定为 1
    agent_name    VARCHAR(100)    NOT NULL DEFAULT 'Jiang I-Agent',
    avatar        VARCHAR(500)    NOT NULL DEFAULT '',
    system_prompt TEXT            NULL,
    model         VARCHAR(50)     NOT NULL DEFAULT 'deepseek-v4-flash',
    temperature   DECIMAL(3,2)    NOT NULL DEFAULT 0.7,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 2.2 对话表

```sql
CREATE TABLE t_conversation (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT UNSIGNED NOT NULL,              -- 所属用户
    title         VARCHAR(200)    NOT NULL DEFAULT '',
    model         VARCHAR(50)     NOT NULL DEFAULT '',
    message_count INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_created (created_at),
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE
);

CREATE TABLE t_message (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT UNSIGNED NOT NULL,
    role            VARCHAR(20)     NOT NULL,              -- user / assistant / tool
    content         MEDIUMTEXT      NULL,                  -- 消息正文
    thinking        MEDIUMTEXT      NULL,                  -- DeepSeek reasoning_content
    tool_calls      JSON            NULL,                  -- 工具调用 JSON
    token_count     INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_msg (conversation_id, created_at),
    FOREIGN KEY (conversation_id) REFERENCES t_conversation(id) ON DELETE CASCADE
);
```

> **thinking 字段**：2026-06-26 新增。之前思考内容用 `<thinking>` HTML 标签包裹在 content 字段中，现在独立存储。

### 2.3 文档表（RAG）

```sql
CREATE TABLE t_document (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    filename     VARCHAR(255)    NOT NULL,
    file_type    VARCHAR(20)     NOT NULL,                  -- md / pdf / txt / docx
    file_size    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    content_hash VARCHAR(64)     NOT NULL DEFAULT '',       -- SHA-256 去重
    chunk_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    status       TINYINT         NOT NULL DEFAULT 0,        -- 0-待处理 1-已解析 2-已向量化
    summary      VARCHAR(500)    NOT NULL DEFAULT '',
    oss_key      VARCHAR(200)    NOT NULL DEFAULT '',       -- OSS 存储 key
    uploaded_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_content_hash (content_hash),
    INDEX idx_status (status)
);

CREATE TABLE t_document_chunk (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    document_id  BIGINT UNSIGNED NOT NULL,
    chunk_index  INT UNSIGNED    NOT NULL,
    content      TEXT            NOT NULL,
    token_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    embedding_id VARCHAR(100)    NOT NULL DEFAULT '',       -- Qdrant Point ID
    UNIQUE KEY uk_doc_chunk (document_id, chunk_index),
    FOREIGN KEY (document_id) REFERENCES t_document(id) ON DELETE CASCADE
);
```

### 2.4 待办表

```sql
CREATE TABLE t_todo_item (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    conversation_id BIGINT UNSIGNED NULL,
    title           VARCHAR(500)    NOT NULL,
    due_date        DATE            NULL,
    is_done         TINYINT         NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME        NULL,
    INDEX idx_done (is_done, due_date)
);
```

### 2.5 工具调用日志（可观测性）

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
| `uk_username` | 用户名唯一，登录查询 |
| `idx_conv_msg(conversation_id, created_at)` | 按会话查消息是最高频查询 |
| `uk_content_hash` | SHA-256 唯一，防止重复上传同一文件 |
| `idx_done(is_done, due_date)` | 查未完成待办按截止日期排序 |
| `idx_user(user_id)` | 按用户隔离会话数据 |
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

-- 模糊搜索概念（前端 GraphPanel 用）
MATCH (c:Concept) WHERE c.name =~ '.*Redis.*' RETURN c LIMIT 20
```

### 图谱防护机制

为防 AI 自主维护导致图谱杂乱，后端实现了三层自动防护：

| 防护 | 机制 | 触发时机 |
|------|------|---------|
| 循环检测 | 添加 PREQ(A,B) 前检查 B→A 路径是否存在 | `addPrerequisite()` |
| 自环预防 | `from.equals(to)` 直接拒绝 | `addPrerequisite()` / `addRelated()` |
| 传递化简 | 添加 PREQ(A,B) 后检测冗余：若 A→B→C 且 A→C 存在 → 删除 A→C | `addPrerequisite()` 后自动执行 |

传递化简算法：
```
方向1: B 有前置 B→C → 若 A→C 存在 → 删除 A→C（A→B→C 可推导）
方向2: P 有前置 P→A → 若 P→B 存在 → 删除 P→B（P→A→B 可推导）
```

### 图可视化

前端 ECharts 配置：
- **布局**：hierarchical LR（左→右树形），physics disabled 保持稳定
- **过滤**：默认"仅前置"（最干净），可切换"仅相关"/"全部"
- **交互**：双击节点加载邻居（去重），拖拽节点，缩放平移
- **删除**：列表页 ✕ 按钮 → `DELETE /api/graph/concepts/{name}`

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

---

## 五、Redis 键设计

| Key 模式 | 类型 | TTL | 用途 |
|----------|------|-----|------|
| `agent:chat:memory:{convId}` | List | 30min | ChatMemory 对话历史 |
| `agent:rate_limit:{key}:{min}` | String | 60s | 令牌桶限流计数器 |
| `agent:session:{sessionId}` | Hash | 30min | 会话元数据 |

---

## 六、鉴权设计

- **方案**：无状态 JWT Token
- **Filter**：`JwtAuthFilter` 拦截 `/api/**`（白名单：`/api/auth/login`、`/api/auth/register`）
- **前端**：`Authorization: Bearer <token>` header，token 存 localStorage
- **用户角色**：USER / ADMIN（admin 可访问 `/api/admin/**`）

---

## 七、SSE 流式协议

流式端点 `GET /api/chat/stream` 返回 `text/event-stream`，每个 `data` 块为 JSON：

```json
{"type":"thinking","content":"思考过程文本片段..."}
{"type":"content","content":"回复文本片段..."}
{"type":"tool_call","name":"search_knowledge","args":"{\"query\":\"...\"}"}
```

| type | 说明 | 前端处理 |
|------|------|---------|
| `thinking` | DeepSeek reasoning_content 增量 | 追加到思考框 |
| `content` | 回复正文增量 | 追加到气泡（打字机效果） |
| `tool_call` | 工具调用开始 | 显示 "调用: toolName"，清 content 暂存 |

> **关键设计**：content 实时逐 chunk 发射（不是缓冲后再一次性发送），tool_call 事件时前端清空中间 content。详见 ISSUES.md §14。

---

## 八、思考框 UI 设计

| 状态 | 行为 |
|------|------|
| **流式中** | 思考框展开，标题 "思考中..." 或 "调用: toolName"，chevron 旋转 |
| **流式结束** | 自动折叠（`thinkingCollapsed[i] = true`），标题 "思考内容" |
| **历史消息** | 默认折叠，点击标题展开/折叠，chevron 旋转动画 200ms |
| **数据存储** | `Message.thinking` 字段独立存储，前端 `m.thinking` 渲染 |

---

## 九、前端设计系统

全局 CSS 自定义属性（`frontend/src/assets/style.css`，30KB）：

```
:root {
  --accent: #F472B6;        --accent-deep: #EC4899;     --accent-light: #FBCFE8;
  --lavender: #8B5CF6;      --lavender-light: #A78BFA;
  --color-error: #EF4444;   --color-success: #22C55E;   --color-warning: #F59E0B;
  --text-primary: #1E293B;  --text-secondary: #64748B;  --text-tertiary: #94A3B8;
  --bg-body: #FDF2F8;       --bg-surface: #FFFFFF;
  --radius-sm: 8px;         --radius: 12px;             --radius-lg: 18px;
  --font-sans: "Inter", ...; --font-mono: "SF Mono", ...;
  /* + 间距/字重/阴影/过渡 scale */
}
```

---

## 十、分阶段建表策略

| 阶段 | 建表 | 外部系统 |
|------|------|---------|
| P1（框架） | `t_user` `t_agent_config` | Redis |
| P2（RAG） | `t_document` `t_document_chunk` | Qdrant |
| P3（图谱） | — | Neo4j |
| P4（工程化） | `t_conversation` `t_message` `t_todo_item` `t_tool_usage_log` | — |
| P5（前端） | `t_message.thinking` 列 | — |
