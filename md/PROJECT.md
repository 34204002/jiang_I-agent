# Jiang I-Agent — 项目总览

> Spring Boot 4.1 + Spring AI 2.0 单智能体个人知识库助手。
>
> RAG 向量检索 + Neo4j 知识图谱双检索，18 个 Agent 工具，Vue 3 SPA 前端。

---

## 项目定位

面向程序员的个人 AI 助理：

- **对话**：多轮对话 + SSE 流式输出（DeepSeek v4-flash thinking 思考模式），Redis 会话记忆 + MySQL 持久化
- **工具调用**：自研 `@Tool` 注解框架，18 个工具自动注册，LLM 自主选择调用，最多 10 轮工具循环
- **RAG 知识库**：文档上传 → Tika 解析 → 分片 → BAAI/bge-m3 向量化 → Qdrant 语义检索增强回答
- **知识图谱**：Neo4j 概念关联建模 + 前置知识链查询（"学 Redis 前需要先学什么"），AI 对话自动沉淀 + 循环检测 + 传递化简 + ECharts 层次化可视化 + 关系过滤
- **待办 & 提醒**：CRUD 待办管理 + 定时提醒（`@Scheduled` 每分钟检查）

**差异化**：不是"查文档 + 喂 LLM"的普通 RAG。Neo4j 图谱返回的是**知识结构**和**学习路径**，纯向量检索做不到。

---

## 架构

```
浏览器 (Vue 3 SPA)
  │
  ├── /api/chat/stream (SSE) ──→ ChatController ──→ ChatService (Agent 核心)
  │                                                      │
  │       ┌──────────┬──────────┬───────┴───────┬──────────┬──────────┐
  │       ▼          ▼          ▼               ▼          ▼          ▼
  │    记忆层      工具层      知识层           图谱层      HTTP 层    鉴权层
  │    Redis      @Tool 自研   Qdrant          Neo4j     DeepSeekApi  JWT
  │    Memory     18 个工具    向量检索         概念关系   (Spring AI)  Filter
  │
  ├── /api/knowledge/* ──→ KnowledgeController ──→ KnowledgeService
  ├── /api/graph/*     ──→ GraphController      ──→ GraphService
  ├── /api/tools       ──→ ToolController       ──→ ToolRegistry
  ├── /api/todos/*     ──→ TodoController       ──→ TodoService
  ├── /api/conversations/* ──→ ConversationController ──→ ConversationService
  ├── /api/auth/*      ──→ AuthController       ──→ AuthService
  └── /api/admin/*     ──→ AdminController
```

---

## 技术栈

| 组件 | 选型 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 4.1.0 |
| AI | Spring AI DeepSeek 专用 API | 2.0.0 |
| 大模型 | DeepSeek 官方 API | v4-flash (默认思考) |
| 嵌入模型 | BAAI/bge-m3（硅基流动） | 1024 维 |
| 前端 | Vue 3 + Vite + vue-router | 3.x / 8.x |
| 图谱可视化 | ECharts + vue-echarts | 5.x |
| Markdown | marked | — |
| 缓存 | Redis + Lettuce | 7.x |
| 向量库 | Qdrant | — |
| 图数据库 | Neo4j | 5.x |
| 关系库 | MySQL + MyBatis-Plus | 8.x |
| 对象存储 | 阿里云 OSS | — |
| 鉴权 | JWT Filter | — |

---

## 前端架构 (Vue 3 SPA)

```
frontend/src/
├── App.vue              # 主布局: Sidebar + 4 Tab (对话/知识库/图谱/工具) + router-view
├── router.js            # /login /chat /settings /admin + 导航守卫
├── stores/state.js      # reactive 全局状态 + loadAgentConfig + logout
├── utils/
│   ├── api.js           # HTTP 客户端 (JWT auth header, 统一错误处理)
│   ├── chat.js          # 会话加载/选择/创建 + thinking 字段解析
│   └── toast.js         # Toast 通知系统
├── assets/style.css     # 全局设计系统 (26KB, CSS 自定义属性, 响应式)
├── components/
│   ├── ChatPanel.vue    # SSE 流式聊天: thinking/content/tool_call 事件分派 + 打字机光标
│   ├── Sidebar.vue      # 会话列表 + 批量删除 + 登出 (SVG 图标)
│   ├── GraphPanel.vue   # Neo4j 图谱: 层次化树形图 + 关系过滤 + 概念删除 + Teleport 模态框
│   ├── KnowledgePanel.vue # RAG 知识库: 文档上传/搜索/列表/删除 (SVG 图标)
│   └── ToolsPanel.vue   # 工具标签卡片 + 待办 CRUD
└── views/
    ├── LoginView.vue     # 登录/注册
    ├── SettingsView.vue  # 个人设置 (头像/昵称/密码)
    └── AdminView.vue     # 管理后台 (用户管理 + Agent 配置)
```

---

## 分阶段路线

| 阶段 | 目标 | 状态 |
|------|------|------|
| **P1** | 核心 Agent 闭环（SSE + ChatMemory + @Tool 框架） | ✅ |
| **P2** | RAG 知识库（文档上传 + Qdrant 向量检索） | ✅ |
| **P3** | Neo4j 知识图谱（概念建模 + 知识链查询 + AI 提取） | ✅ |
| **P4** | 工程化增强（MySQL + 前端 + 鉴权 + 批量删除 + 登出） | ✅ |
| **P5** | Vue 3 SPA 重写（SFC 组件 + 设计系统 + 打字机恢复 + 思考框折叠） | ✅ |

---

## 工具清单（18 个）

| 类别 | 工具 | 说明 |
|------|------|------|
| 待办 | `create_todo` `list_todos` `complete_todo` `delete_todo` | 全生命周期管理 |
| 提醒 | `create_reminder` `list_reminders` `cancel_reminder` | 定时提醒，@Scheduled 检查 |
| 知识库 | `search_knowledge` `list_knowledge` | Qdrant 语义检索 + 文档列表 |
| 知识图谱 | `search_concepts` `find_learning_path` `add_concept` | 搜索/路径/沉淀 + 循环检测 + 传递化简 |
| 时间 | `get_current_time` | 当前时间 |
| 网络 | `read_web_page` `search_web` | 网页抓取 + 联网搜索 |
| 对话 | `search_conversation` `export_conversation` | 历史搜索 + Markdown 导出 |
| 系统 | `get_status` | Agent 运行状态 |

---

## 接口总览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 JWT token |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/chat` | 同步对话 |
| GET | `/api/chat/stream` | SSE 流式对话（?thinking=true 开启思考模式） |
| POST | `/api/knowledge/documents` | 上传文档 |
| GET | `/api/knowledge/documents` | 文档列表 |
| DELETE | `/api/knowledge/documents/{id}` | 删除文档 |
| GET | `/api/knowledge/documents/{id}/download` | 下载文档 |
| POST | `/api/knowledge/search` | RAG 知识库问答 |
| GET | `/api/graph/concepts` | 概念列表/搜索 |
| GET | `/api/graph/concepts/{name}` | 概念详情+关联 |
| GET | `/api/graph/concepts/{name}/path` | 知识链查询 |
| GET | `/api/graph/concepts/{name}/graph` | 子图（ECharts） |
| POST | `/api/graph/concepts` | 手动添加概念 |
| DELETE | `/api/graph/concepts/{name}` | 删除概念（级联删关系） |
| DELETE | `/api/graph/concepts/{name}/relations` | 删除关系 |
| GET | `/api/tools` | 工具列表 |
| GET/PUT/DELETE | `/api/todos/*` | 待办 CRUD |
| GET | `/api/conversations` | 会话列表 |
| GET | `/api/conversations/{id}/messages` | 会话消息 (含 thinking 字段) |
| DELETE | `/api/conversations/{id}` | 删除会话 |
| POST | `/api/conversations/batch-delete` | 批量删除会话 |
| GET/PUT | `/api/admin/agent` | Agent 配置 |
| GET/DELETE | `/api/admin/users/*` | 用户管理 |
| GET/POST/PUT | `/api/profile/*` | 个人设置 |

---

## 快速启动

```bash
# 1. 启动基础设施: MySQL(3306) + Redis(6379) + Neo4j(7687) + Qdrant(6334)
# 2. 初始化数据库
mysql -u root < src/main/resources/sql/schema.sql

# 3. 已有数据库执行迁移 (如果是从旧版升级)
mysql -u root jiang_i_agent < src/main/resources/sql/migration_add_thinking.sql

# 4. 启动后端
./mvnw spring-boot:run

# 5. 启动前端开发服务器 (可选，生产环境用编译后的静态资源)
cd frontend && npm install && npm run dev
```

---

## 设计系统

前端使用完整 CSS 自定义属性体系（`frontend/src/assets/style.css`）：

- **主色**：`--accent: #F472B6`（淡粉）+ `--accent-deep: #EC4899`（深粉渐变）+ `--lavender: #8B5CF6`（紫色辅助）
- **语义色**：`--color-error: #EF4444` / `--color-success: #22C55E` / `--color-warning: #F59E0B`
- **字体**：Inter 优先，等宽代码（`--font-mono`）
- **间距/字重/阴影/过渡**：统一 token，无硬编码值
- **思考框**：紫色边框 + 折叠动画 + chevron 旋转
