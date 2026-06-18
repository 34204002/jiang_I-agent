# Jiang I-Agent

> 基于 Spring AI 2.0 的单智能体个人知识库助手 — 非玩具 Demo，完整 Agent 闭环。
>
> RAG 向量检索 + Neo4j 知识图谱双检索，Java 后端向，适合写进简历、面试深挖。

---

## 项目定位

面向程序员的个人 AI 助理，支持：

- **对话**：多轮对话 + SSE 流式输出，Redis 持久化会话记忆
- **工具调用**：`@Tool` 注解自动注册，LLM 自主选择调用（待办管理、知识库问答等）
- **RAG 知识库**：文档上传 → 解析分片 → Qdrant 向量化 → 语义检索增强回答
- **知识图谱**：Neo4j 知识点关联建模，支持前置知识链查询（"学 Redis 前需要先学什么"）

**差异化**：不是"查文档 + 喂 LLM"的普通 RAG。Neo4j 图谱返回的是**知识结构**和**学习路径**，纯向量检索做不到。

---

## 架构

```
客户端 → ChatController → ChatService → ChatClient (Spring AI)
                                            │
         ┌──────────┬──────────┬───────────┼───────────┬──────────┐
         ▼          ▼          ▼           ▼           ▼          ▼
      记忆层      规划层      工具层      知识层       安全层
      Redis       LLM        @Tool       Qdrant      Redisson
      Memory      意图识别    自动注册     + Neo4j     令牌桶限流
```

---

## 技术栈

| 组件 | 选型 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 4.1.0 |
| AI | Spring AI（OpenAI 适配器） | 2.0.0 |
| 大模型 | DeepSeek（via 硅基流动） | V3.2 |
| 嵌入模型 | text-embedding-3-small | — |
| 缓存 | Redis + Lettuce | 7.x |
| 向量库 | Qdrant | — |
| 图数据库 | Neo4j | 5.x |
| 关系库 | MySQL + MyBatis-Plus | 8.x |

---

## 分阶段路线

| 阶段 | 目标 | 关键产出 | 状态 |
|------|------|---------|------|
| **P1** | 核心 Agent 闭环 | ChatController(SSE) + ChatMemory + @Tool | 🔜 |
| **P2** | RAG 知识库 | 文档上传 + Qdrant 向量检索 + KnowledgeQATool | ⏳ |
| **P3** | 知识图谱 | Neo4j Schema + 双检索融合 + 知识链查询 | ⏳ |
| **P4** | 工程化增强 | MySQL 持久化 + MQ 异步 + 限流 + 前端 | ⏳ |

---

## 当前项目结构

```
├── pom.xml                    # 9 个依赖，按需扩展
├── PROJECT.md                 # 项目总览（本文档）
├── API_DESIGN.md              # 接口文档
├── DESIGN.md                  # 技术设计（DB / Neo4j / Redis）
└── src/main/java/com/jiang/
    ├── JiangIAgentApplication.java
    ├── common/
    │   ├── Result.java
    │   ├── BizException.java
    │   └── GlobalExceptionHandler.java
    └── config/
        ├── ChatClientConfig.java
        └── RedisConfig.java
```

## 接口总览

详见 [API_DESIGN.md](API_DESIGN.md)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 同步对话 |
| GET | `/api/chat/stream` | SSE 流式对话 |
| POST | `/api/knowledge/documents` | 上传文档 |
| GET | `/api/knowledge/documents` | 文档列表 |
| DELETE | `/api/knowledge/documents/{id}` | 删除文档 |
| POST | `/api/knowledge/search` | RAG 知识库问答 |
| GET | `/api/graph/concepts/{name}` | 概念详情+关联 |
| GET | `/api/graph/concepts/{name}/path` | 知识链查询 |
| GET | `/api/conversations` | 会话历史 |

---

## 快速启动

```bash
# 本地需启动：MySQL(3306) + Redis(6379) + Neo4j(7687) + Qdrant(6334)
# application-dev.yml 已配置好连接信息
./mvnw spring-boot:run
```
