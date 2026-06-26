# Jiang I-Agent

> 基于 Spring Boot 4.1 + Spring AI 2.0 的单智能体个人知识库助手。
>
> RAG 向量检索 + Neo4j 知识图谱双检索，17 个 Agent 工具，Java 21 后端。

---

## 项目定位

面向程序员的个人 AI 助理：

- **对话**：多轮对话 + SSE 流式输出（thinking 思考模式），Redis 持久化会话记忆
- **工具调用**：自研 `@Tool` 注解框架，17 个工具自动注册，LLM 自主选择调用，最多 5 轮工具循环
- **RAG 知识库**：文档上传 → Tika 解析 → 分片向量化 → Qdrant 语义检索增强回答
- **知识图谱**：Neo4j 概念关联建模 + 前置知识链查询（"学 Redis 前需要先学什么"），AI 文档提取 + Agent 对话自动沉淀
- **待办 & 提醒**：CRUD 待办管理 + 定时提醒（`@Scheduled` 每分钟检查）

**差异化**：不是"查文档 + 喂 LLM"的普通 RAG。Neo4j 图谱返回的是**知识结构**和**学习路径**，纯向量检索做不到。

---

## 架构

```
浏览器 → ChatController(SSE) → ChatService (Agent 核心)
                                    │
         ┌──────────┬──────────┬────┴────┬──────────┐
         ▼          ▼          ▼         ▼          ▼
      记忆层      工具层      知识层     图谱层      HTTP 层
      Redis      @Tool 自研   Qdrant    Neo4j     DeepSeekApi
      Memory     17 个工具    向量检索   概念关系   (Spring AI)
```

---

## 技术栈

| 组件 | 选型 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 4.1.0 |
| AI | Spring AI DeepSeek 专用 API | 2.0.0 |
| 大模型 | DeepSeek 官方 API | v4-flash |
| 嵌入模型 | BAAI/bge-m3（硅基流动） | 1024 维 |
| 缓存 | Redis + Lettuce | 7.x |
| 向量库 | Qdrant | — |
| 图数据库 | Neo4j | 5.x |
| 关系库 | MySQL + MyBatis-Plus | 8.x |
| 对象存储 | 阿里云 OSS | — |

---

## 分阶段路线

| 阶段 | 目标 | 状态 |
|------|------|------|
| **P1** | 核心 Agent 闭环（SSE + ChatMemory + @Tool 框架） | ✅ |
| **P2** | RAG 知识库（文档上传 + Qdrant 向量检索） | ✅ |
| **P3** | Neo4j 知识图谱（概念建模 + 知识链查询 + AI 提取） | ✅ |
| **P4** | 工程化增强（MySQL 持久化 + 前端 + 批量删除 + 鉴权） | ✅ |

---

## 工具清单（17 个）

| 类别 | 工具 |
|------|------|
| 待办 | `create_todo` `list_todos` `complete_todo` `delete_todo` |
| 提醒 | `create_reminder` `list_reminders` `cancel_reminder` |
| 知识库 | `search_knowledge` `list_knowledge` |
| 知识图谱 | `search_concepts` `find_learning_path` `add_concept` |
| 时间 | `get_current_time` |
| 网络 | `read_web_page` |
| 对话 | `search_conversation` `export_conversation` |
| 系统 | `get_status` |

---

## 接口总览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 同步对话 |
| GET | `/api/chat/stream` | SSE 流式对话（支持 thinking 模式） |
| POST | `/api/knowledge/documents` | 上传文档 |
| GET | `/api/knowledge/documents` | 文档列表 |
| DELETE | `/api/knowledge/documents/{id}` | 删除文档 |
| POST | `/api/knowledge/search` | RAG 知识库问答 |
| GET | `/api/graph/concepts` | 概念列表/搜索 |
| GET | `/api/graph/concepts/{name}` | 概念详情+关联 |
| GET | `/api/graph/concepts/{name}/path` | 知识链查询 |
| POST | `/api/graph/concepts` | 手动添加概念 |
| POST | `/api/graph/concepts/{name}/relations` | 添加关系 |
| GET | `/api/tools` | 工具列表 |
| GET | `/api/conversations` | 会话列表 |
| GET | `/api/conversations/{id}/messages` | 会话消息 |
| DELETE | `/api/conversations/{id}` | 删除会话 |
| DELETE | `/api/conversations/batch-delete` | 批量删除会话 |

---

## 快速启动

```bash
# 本地需启动：MySQL(3306) + Redis(6379) + Neo4j(7687) + Qdrant(6334)
# application-dev.yml 已配置好连接信息
./mvnw spring-boot:run
```
