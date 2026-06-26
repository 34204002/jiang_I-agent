# Jiang I-Agent — 接口设计文档

> Base URL: `http://localhost:8080`
> Content-Type: `application/json`
> 统一响应格式: `{"code": 200, "message": "success", "data": {...}}`

---

## 一、对话接口（Phase 1）

### 1.1 同步对话

```
POST /api/chat
```

**请求体：**

```json
{
  "message": "帮我记一下明天下午3点面试",
  "conversationId": "abc123"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | String | 是 | 用户消息 |
| conversationId | String | 否 | 会话 ID，不传则自动创建新会话 |

**响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": "已帮你记下待办：明天下午3点面试 [1]",
    "conversationId": "abc123",
    "toolsCalled": ["todoManager"]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| content | String | AI 回复正文 |
| conversationId | String | 会话 ID（后续请求带上可延续上下文） |
| toolsCalled | String[] | 本次调用的工具名称列表，无工具调用时为空数组 |

---

### 1.2 流式对话（SSE）

```
GET /api/chat/stream?message={msg}&conversationId={cid}
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | String | 是 | 用户消息 |
| conversationId | String | 否 | 会话 ID |

**响应：** `text/event-stream`，每个 data 块为一个 token：

```
data: 已
data: 帮
data: 你
data: 记下
data: 待办...
```

**前端接入示例：**

```javascript
const eventSource = new EventSource(
  '/api/chat/stream?message=你好&conversationId=abc123'
);
eventSource.onmessage = (e) => console.log(e.data);  // 逐 token 输出
eventSource.onerror = () => eventSource.close();      // 出错或结束时关闭
```

---

## 二、知识库接口（Phase 2 — RAG）

### 2.1 上传文档

```
POST /api/knowledge/documents
Content-Type: multipart/form-data
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 支持 .md / .txt / .pdf（最大 20MB） |

**响应：**

```json
{
  "code": 200,
  "data": {
    "id": 1,
    "filename": "Redis实战笔记.md",
    "fileType": "md",
    "fileSize": 15360,
    "chunkCount": 5,
    "status": 1
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 文档 ID |
| filename | String | 原始文件名 |
| fileType | String | md / txt / pdf / docx |
| fileSize | Long | 字节数 |
| chunkCount | Integer | 文本分片数 |
| status | Integer | 0-待处理 1-已解析 2-已向量化 |

---

### 2.2 文档列表

```
GET /api/knowledge/documents?page=1&size=20
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "total": 12,
    "page": 1,
    "size": 20,
    "records": [
      {
        "id": 1,
        "filename": "Redis实战笔记.md",
        "fileType": "md",
        "fileSize": 15360,
        "chunkCount": 5,
        "status": 2,
        "summary": "Redis核心数据结构与持久化方案总结",
        "uploadedAt": "2026-06-17T10:00:00"
      }
    ]
  }
}
```

---

### 2.3 删除文档

```
DELETE /api/knowledge/documents/{id}
```

**响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

> 删除时同步清除向量库中的关联向量和 MySQL 分片记录。

---

### 2.4 知识库问答（RAG 检索）

```
POST /api/knowledge/search
```

**请求体：**

```json
{
  "query": "Redis 持久化有哪几种方式",
  "topK": 5
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | String | 是 | 自然语言查询 |
| topK | Integer | 否 | 返回片段数，默认 5 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "answer": "Redis 提供三种持久化方式：RDB 快照、AOF 日志、混合持久化...",
    "sources": [
      {
        "documentId": 1,
        "filename": "Redis实战笔记.md",
        "chunkIndex": 3,
        "content": "Redis 持久化主要有 RDB 和 AOF 两种方式...",
        "score": 0.923
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| answer | String | LLM 增强后的回答 |
| sources[].documentId | Long | 来源文档 ID |
| sources[].filename | String | 来源文件名 |
| sources[].score | Double | 向量相似度（0~1） |

---

## 三、知识图谱接口（Phase 3）

### 3.1 概念列表

```
GET /api/graph/concepts?category={cat}&page=1&size=20
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "total": 45,
    "records": [
      {
        "name": "Redis",
        "description": "内存键值数据库...",
        "category": "中间件",
        "difficulty": 3,
        "relationCount": 8
      }
    ]
  }
}
```

---

### 3.2 概念详情（含关联）

```
GET /api/graph/concepts/{name}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "name": "Redis",
    "description": "内存键值数据库，支持持久化、主从、哨兵、集群模式",
    "category": "中间件",
    "difficulty": 3,
    "prerequisites": [
      { "name": "数据结构", "difficulty": 2 },
      { "name": "网络编程", "difficulty": 3 }
    ],
    "relatedConcepts": [
      { "name": "Memcached", "relation": "同类竞品" },
      { "name": "Redisson", "relation": "Java客户端" }
    ],
    "documents": [
      { "documentId": 1, "filename": "Redis实战笔记.md" }
    ]
  }
}
```

---

### 3.3 知识链查询（核心差异化）

```
GET /api/graph/concepts/{name}/path?target={targetName}&maxHops=3
```

**示例：** `GET /api/graph/concepts/数据结构/path?target=Redis&maxHops=5`

**响应：**

```json
{
  "code": 200,
  "data": {
    "paths": [
      {
        "nodes": [
          { "name": "数据结构", "difficulty": 2 },
          { "name": "哈希表", "difficulty": 2 },
          { "name": "Redis", "difficulty": 3 }
        ],
        "length": 2
      }
    ]
  }
}
```

> **面试要点**：这个接口是 Neo4j 知识图谱的核心价值——返回"学习路径"而非"文档相似度"。

---

## 四、工具接口（Phase 4）

### 4.1 可用工具列表

```
GET /api/tools
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "tools": [
      {
        "name": "todoManager",
        "description": "管理用户待办任务，支持创建/列表/完成/删除"
      },
      {
        "name": "knowledgeQA",
        "description": "基于知识库的语义问答，检索相关文档后回答"
      }
    ]
  }
}
```

---

## 五、历史记录接口（Phase 4）

### 5.1 会话列表

```
GET /api/conversations?page=1&size=20
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "total": 30,
    "records": [
      {
        "id": 1,
        "title": "Redis持久化方式讨论",
        "model": "deepseek-chat",
        "messageCount": 12,
        "createdAt": "2026-06-17T09:00:00",
        "updatedAt": "2026-06-17T10:30:00"
      }
    ]
  }
}
```

---

### 5.2 会话消息列表

```
GET /api/conversations/{id}/messages?page=1&size=50
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "role": "user",
        "content": "Redis持久化有哪几种方式",
        "createdAt": "2026-06-17T10:00:00"
      },
      {
        "id": 2,
        "role": "assistant",
        "content": "Redis提供三种持久化方式...",
        "toolCalls": [
          { "name": "knowledgeQA", "input": {"query":"Redis持久化"}, "output":"..." }
        ],
        "tokenCount": 256,
        "createdAt": "2026-06-17T10:00:05"
      }
    ]
  }
}
```

---

### 5.3 删除会话

```
DELETE /api/conversations/{id}
```

响应同 2.3。级联删除关联消息。

---

### 5.4 批量删除会话

```
POST /api/conversations/batch-delete
```

**请求体：**

```json
{
  "ids": [1, 2, 3]
}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "deleted": 3,
    "total": 3
  }
}
```

> 逐一校验归属，跳过无权或已删除的会话。

---

## 六、错误码

| code | 说明 | 触发场景 |
|------|------|---------|
| 200 | 成功 | - |
| 400 | 参数错误 | message 为空、文件格式不支持 |
| 413 | 文件过大 | 超过 20MB |
| 429 | 请求过于频繁 | 触发令牌桶限流 |
| 500 | 服务器内部错误 | 未捕获异常 |

**错误响应示例：**

```json
{
  "code": 400,
  "message": "文件格式不支持，仅支持 md/txt/pdf/docx",
  "data": null
}
```

---

## 七、接口汇总

| 阶段 | 方法 | 路径 | 说明 |
|------|------|------|------|
| P1 | POST | `/api/chat` | 同步对话 |
| P1 | GET | `/api/chat/stream` | SSE 流式对话（支持 thinking 模式） |
| P2 | POST | `/api/knowledge/documents` | 上传文档 |
| P2 | GET | `/api/knowledge/documents` | 文档列表 |
| P2 | DELETE | `/api/knowledge/documents/{id}` | 删除文档 |
| P2 | POST | `/api/knowledge/search` | RAG 知识库问答 |
| P3 | GET | `/api/graph/concepts` | 概念列表/搜索 |
| P3 | GET | `/api/graph/concepts/{name}` | 概念详情+关联 |
| P3 | GET | `/api/graph/concepts/{name}/path` | 知识链查询 |
| P3 | POST | `/api/graph/concepts` | 手动添加概念 |
| P3 | POST | `/api/graph/concepts/{name}/relations` | 添加关系 |
| P4 | GET | `/api/tools` | 工具列表 |
| P4 | GET | `/api/conversations` | 会话列表 |
| P4 | GET | `/api/conversations/{id}/messages` | 消息列表 |
| P4 | DELETE | `/api/conversations/{id}` | 删除会话 |
| P4 | POST | `/api/conversations/batch-delete` | 批量删除会话 |
