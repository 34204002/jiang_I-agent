# Jiang I-Agent

基于 Spring AI 2.0 构建的个人 AI 知识库助手，集成 RAG 向量检索、Neo4j 知识图谱、Redis 会话记忆、Function Calling 工具调用。

## 技术栈

| 层级 | 技术 |
|------|------|
| 框架 | Spring Boot 4.1 + Spring AI 2.0 |
| 语言 | Java 21 |
| 大模型 | DeepSeek V3.2 (via SiliconFlow OpenAI 兼容接口) |
| 向量库 | Qdrant (gRPC) |
| 图数据库 | Neo4j |
| 关系库 | MySQL 8 + MyBatis-Plus |
| 缓存/记忆 | Redis (ChatMemory 手动实现) |
| 前端 | HTML + CSS + JS (EventSource SSE) |

## 项目结构

```
src/main/java/com/jiang/
├── common/          # Result、BizException、GlobalExceptionHandler
├── config/          # ChatClient、Redis、MybatisPlus、RedisChatMemory
├── controller/      # Chat / Knowledge / Graph / Conversation / Tool
├── entity/          # MySQL 实体类 (MyBatis-Plus)
├── mapper/          # MyBatis-Plus Mapper 接口
├── model/
│   ├── req/         # 请求 DTO
│   ├── resp/        # 响应 DTO
│   └── vo/          # 视图对象
└── service/         # 业务服务
src/main/resources/
├── prompts/         # Agent 系统提示词
├── sql/             # MySQL DDL
└── static/          # 前端静态资源 (index.html + css/ + js/)
md/                  # 项目文档
```

## 快速开始

### 环境要求

- Java 21+
- MySQL 8+
- Redis（无需 Redis Stack）
- Neo4j
- Qdrant

### 1. 初始化数据库

```sql
source src/main/resources/sql/schema.sql
```

### 2. 配置密钥

编辑 `src/main/resources/application-dev.yml`，填写 API Key 和各服务连接信息。

### 3. 启动

```bash
./mvnw spring-boot:run
```

### 4. 访问

浏览器打开 `http://localhost:8080`

## 核心特性

- **SSE 流式对话** — EventSource + 打字机逐字效果
- **会话记忆** — Redis ChatMemory 30min TTL，MySQL 持久化
- **多轮对话** — 新会话自动创建，历史会话可切换/删除
- **系统提示词** — `prompts/system.md` 定义 Agent 身份，可热编辑
- **预留扩展** — RAG 知识库、Neo4j 图谱、@Tool 工具调用框架已就绪

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 同步对话 |
| GET/POST | `/api/chat/stream` | SSE 流式对话 |
| GET | `/api/conversations` | 会话列表 |
| GET | `/api/conversations/{id}/messages` | 会话消息 |
| DELETE | `/api/conversations/{id}` | 删除会话 |
| POST | `/api/knowledge/*` | 知识库 (Phase 2) |
| GET | `/api/graph/*` | 知识图谱 (Phase 3) |
| GET | `/api/tools` | 工具列表 (Phase 4) |

## 开发阶段

| Phase | 内容 | 状态 |
|-------|------|------|
| P1 | 基础对话 + ChatMemory + MySQL CRUD | ✅ 完成 |
| P2 | RAG 知识库（文档上传 + 向量检索） | ⏳ 待开发 |
| P3 | Neo4j 知识图谱（概念关联 + 双检索） | ⏳ 待开发 |
| P4 | @Tool 工具调用 + 待办 + 工程优化 | ⏳ 待开发 |
