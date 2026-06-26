# Jiang I-Agent — 接口设计文档

> Base URL: `http://localhost:8080`
> Content-Type: `application/json`
> 鉴权 Header: `Authorization: Bearer <token>`（除 `/api/auth/*` 外所有 `/api/**` 需要）
> 统一响应格式: `{"code": 200, "message": "success", "data": {...}}`

---

## 一、鉴权接口

### 1.1 登录

```
POST /api/auth/login
```

**请求体：**

```json
{
  "username": "admin",
  "password": "123456"
}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "user": { "id": 1, "username": "admin", "nickname": "Admin", "role": "ADMIN", "avatar": "" }
  }
}
```

### 1.2 注册

```
POST /api/auth/register
```

**请求体：**

```json
{
  "username": "newuser",
  "password": "123456",
  "nickname": "新用户"
}
```

---

## 二、对话接口

### 2.1 同步对话

```
POST /api/chat
```

**请求体：**

```json
{
  "message": "帮我记一下明天下午3点面试",
  "conversationId": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | String | 是 | 用户消息 |
| conversationId | Long | 否 | 会话 ID，不传则自动创建新会话 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "content": "已帮你记下待办：明天下午3点面试",
    "conversationId": 1,
    "toolsCalled": ["create_todo"]
  }
}
```

---

### 2.2 流式对话（SSE）★ 核心

```
GET /api/chat/stream?message={msg}&conversationId={cid}&token={jwt}&thinking=true
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | String | 是 | 用户消息（需 URL encode） |
| conversationId | Long | 否 | 会话 ID |
| token | String | 是 | JWT token |
| thinking | Boolean | 否 | 是否开启思考模式（默认 false，传 true 启用） |

**响应：** `text/event-stream`，每个 data 块为 JSON 对象：

```
data: {"type":"thinking","content":"用户问的是Redis持久化..."}
data: {"type":"content","content":"Redis"}
data: {"type":"content","content":" 提供"}
data: {"type":"content","content":"三种持久化方式"}
data: {"type":"tool_call","name":"search_knowledge","args":"{\"query\":\"Redis持久化\"}"}
data: {"type":"content","content":"根据知识库..."}
```

| type | 说明 | 前端行为 |
|------|------|---------|
| `thinking` | DeepSeek reasoning_content 增量 | 追加到思考框 |
| `content` | 回复正文增量（实时逐 chunk，打字机效果） | 追加到消息气泡 + 光标闪烁 |
| `tool_call` | 工具调用开始 | 更新 thinkHdr，清空 content 暂存 |

**前端接入示例：**

```javascript
const url = `/api/chat/stream?message=${encodeURIComponent(text)}&token=${token}&thinking=true`
const es = new EventSource(url)
es.onmessage = e => {
  const evt = JSON.parse(e.data)
  if (evt.type === 'thinking') streamThinking += evt.content
  if (evt.type === 'content') streamContent += evt.content
  if (evt.type === 'tool_call') { /* 清 content, 显示工具名 */ }
}
es.onerror = () => { es.close(); /* 保存消息到 state */ }
```

---

## 三、知识库接口（RAG）

### 3.1 上传文档

```
POST /api/knowledge/documents
Content-Type: multipart/form-data
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 支持 .md / .txt / .pdf / .docx |

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
| status | Integer | 0-待处理 1-已解析 2-已向量化 |

### 3.2 文档列表

```
GET /api/knowledge/documents?page=1&size=20
```

### 3.3 删除文档

```
DELETE /api/knowledge/documents/{id}
```

> 删除时同步清除 Qdrant 向量和 MySQL 分片记录。

### 3.4 下载文档

```
GET /api/knowledge/documents/{id}/download
```

> 从阿里云 OSS 302 重定向下载。

### 3.5 知识库问答（RAG 检索）

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

---

## 四、知识图谱接口（Neo4j）

### 4.1 概念列表/搜索

```
GET /api/graph/concepts?keyword={kw}&category={cat}&page=1&size=20
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

> 搜索使用 Neo4j `=~` 操作符进行模糊正则匹配。

### 4.2 概念详情（含关联）

```
GET /api/graph/concepts/{name}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "name": "Redis",
    "description": "...",
    "category": "中间件",
    "difficulty": 3,
    "prerequisites": [
      { "name": "数据结构", "difficulty": 2 }
    ],
    "related": [
      { "name": "Memcached", "relation": "同类竞品" }
    ],
    "documents": [
      { "documentId": 1, "filename": "Redis实战笔记.md" }
    ]
  }
}
```

### 4.3 知识链查询（核心差异化）

```
GET /api/graph/concepts/{name}/path?target={targetName}&maxHops=5
```

**示例：** `GET /api/graph/concepts/数据结构/path?target=Redis&maxHops=5`

**响应：**

```json
{
  "code": 200,
  "data": {
    "paths": [
      ["数据结构", "哈希表", "Redis"]
    ]
  }
}
```

> 查询策略：PREREQUISITE_OF → RELATED_TO → 任意关系 fallback。

### 4.4 子图（vis-network 可视化）

```
GET /api/graph/concepts/{name}/graph
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "nodes": [
      { "id": "Redis", "category": "中间件", "center": true }
    ],
    "edges": [
      { "from": "数据结构", "to": "Redis", "label": "PREREQUISITE_OF" }
    ]
  }
}
```

### 4.5 手动添加概念

```
POST /api/graph/concepts
```

**请求体：**

```json
{
  "name": "Redis",
  "description": "内存键值数据库",
  "category": "中间件",
  "difficulty": 3
}
```

---

## 五、工具 + 待办接口

### 5.1 工具列表

```
GET /api/tools
```

**响应：**

```json
{
  "code": 200,
  "data": [
    { "name": "search_knowledge", "description": "搜索知识库" },
    { "name": "create_todo", "description": "创建待办" }
  ]
}
```

> 注：`data` 是数组（不是 `{tools: [...]}` 对象）。

### 5.2 待办 CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/todos?done=false&page=1&size=50` | 待办列表 |
| POST | `/api/todos` | 创建待办 `{"title":"..."}` |
| PUT | `/api/todos/{id}/complete` | 标记完成 |
| DELETE | `/api/todos/{id}` | 删除待办 |

---

## 六、历史记录接口

### 6.1 会话列表

```
GET /api/conversations?page=1&size=50
```

### 6.2 会话消息列表

```
GET /api/conversations/{id}/messages?page=1&size=200
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
        "thinking": null,
        "createdAt": "2026-06-26T10:00:00"
      },
      {
        "id": 2,
        "role": "assistant",
        "content": "Redis提供三种持久化方式...",
        "thinking": "用户询问Redis持久化方案，需要从知识库检索...",
        "toolCalls": null,
        "tokenCount": 256,
        "createdAt": "2026-06-26T10:00:05"
      }
    ]
  }
}
```

> **thinking 字段**：独立字段，存储 DeepSeek reasoning_content。前端在思考框中渲染，默认折叠。

### 6.3 删除会话

```
DELETE /api/conversations/{id}
```

> 校验归属，级联删除消息。

### 6.4 批量删除会话

```
POST /api/conversations/batch-delete
```

**请求体：**

```json
{
  "ids": [1, 2, 3]
}
```

---

## 七、管理接口

### 7.1 Agent 配置

```
GET /api/admin/agent        # 获取配置
PUT /api/admin/agent        # 更新配置 {"agentName":"...","model":"...","temperature":0.7,"systemPrompt":"..."}
```

### 7.2 用户管理

```
GET /api/admin/users?page=1&size=100    # 用户列表
DELETE /api/admin/users/{id}            # 删除用户
```

### 7.3 个人设置

```
GET /api/profile             # 获取个人信息
POST /api/profile/avatar     # 上传头像 (multipart)
PUT /api/profile             # 更新信息 {"nickname":"...","password":"..."}
```

---

## 八、错误码

| code | 说明 | 触发场景 |
|------|------|---------|
| 200 | 成功 | - |
| 400 | 参数错误 | message 为空、文件格式不支持 |
| 401 | 未登录 | token 缺失或过期 |
| 403 | 无权访问 | 越权访问他人会话 |
| 404 | 资源不存在 | 会话/文档/概念不存在 |
| 413 | 文件过大 | 超过上传限制 |
| 429 | 请求频繁 | 触发限流 |
| 500 | 服务器错误 | 未捕获异常 |

---

## 九、接口汇总

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/chat` | 同步对话 |
| GET | `/api/chat/stream` | SSE 流式对话 (?thinking=true) |
| POST | `/api/knowledge/documents` | 上传文档 |
| GET | `/api/knowledge/documents` | 文档列表 |
| DELETE | `/api/knowledge/documents/{id}` | 删除文档 |
| GET | `/api/knowledge/documents/{id}/download` | 下载文档 |
| POST | `/api/knowledge/search` | RAG 知识库问答 |
| GET | `/api/graph/concepts` | 概念列表/搜索 |
| GET | `/api/graph/concepts/{name}` | 概念详情 |
| GET | `/api/graph/concepts/{name}/path` | 知识链查询 |
| GET | `/api/graph/concepts/{name}/graph` | 子图 (vis-network) |
| POST | `/api/graph/concepts` | 添加概念 |
| GET | `/api/tools` | 工具列表 |
| GET/POST/PUT/DELETE | `/api/todos[/{id}/complete]` | 待办 CRUD |
| GET | `/api/conversations` | 会话列表 |
| GET | `/api/conversations/{id}/messages` | 消息列表 (含 thinking) |
| DELETE | `/api/conversations/{id}` | 删除会话 |
| POST | `/api/conversations/batch-delete` | 批量删除 |
| GET/PUT | `/api/admin/agent` | Agent 配置 |
| GET/DELETE | `/api/admin/users/*` | 用户管理 |
| GET/POST/PUT | `/api/profile/*` | 个人设置 |
