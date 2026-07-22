sh# Jiang I-Agent

> 个人 AI 知识库助手 —— Spring Boot 4.1 + DeepSeek v4-flash + Neo4j + Qdrant + Vue 3 SPA

一个面向程序员的单智能体个人 AI 助理。支持多轮对话（SSE 流式 + thinking 思考模式 + 上下文摘要压缩）、RAG 知识库（Qdrant 向量检索）、Neo4j 知识图谱（概念建模 + 学习路径查询）、18 个 Agent 工具自主调用。前端 Vue 3 SPA，完整设计系统。

## 本项目开源仅供学习交流，禁止用于毕设 / 面试抄袭等违规用途

项目演示链接：https://www.bilibili.com/video/BV17F7W6GEnM/?spm_id_from=333.1387.homepage.video_card.click

![Jiang I-Agent 截图](https://jiang-learning.oss-cn-beijing.aliyuncs.com/Snipaste_2026-06-27_16-24-58.png)

---

## 特性

### 对话
- **SSE 流式输出**：fetch + ReadableStream 流式读取，逐字打字机效果，防无限重连保护
- **拖拽上传文件**：桌面拖入 PDF/MD/TXT/DOCX → 自动 Tika 解析 → AI 理解文件内容
- **DeepSeek thinking 思考模式**：思考过程可视化，思考框自动折叠 + chevron 切换
- **多轮对话**：Redis ChatMemory 30min TTL，MySQL 持久化历史，上下文摘要自动压缩
- **工具调用**：LLM 自主选择 18 个工具，最多 10 轮工具循环，并行调用支持

### 知识库（RAG）
- 文档上传（PDF/Markdown/TXT/DOCX）→ Tika 解析 → BAAI/bge-m3 向量化 → Qdrant 语义检索
- SHA-256 去重，阿里云 OSS 存储，下载功能
- LLM 增强问答，返回来源片段 + 相似度

### 知识图谱（Neo4j）
- 概念节点 + PREREQUISITE_OF / RELATED_TO 关系
- **知识链查询**："学 Redis 前需要先学什么" → 前置知识路径
- ECharts 层次化树形图（LR）：双击节点加载邻居，关系过滤（仅前置/仅相关/全部）
- 循环检测 + 传递化简 + 自环预防：后端自动清理冗余边
- AI 对话自动沉淀概念，手动添加/编辑/删除

### 工具 & 待办
- 18 个 Agent 工具（待办/提醒/知识库/图谱/时间/网络/对话/系统）
- 待办 CRUD + 定时提醒（@Scheduled）
- 工具调用日志可观测性

### 前端
- Vue 3 + Vite + vue-router SPA
- CSS 自定义属性设计系统（淡粉 + 紫色辅助色）
- SVG 图标（无 emoji），响应式，WCAG 无障碍
- 思考框折叠动画 + 打字机光标

### 鉴权 & 安全
- JWT 无状态认证，BCrypt 密码加密
- USER / ADMIN 角色隔离
- Bucket4j 令牌桶限流（30 tokens，≈60 req/min 每用户，429 超限）
- 个人设置（头像/昵称/密码）+ 管理后台

---

## 架构

```
┌─────────────────────────────────────────────────────┐
│                    浏览器 (Vue 3 SPA)                 │
│  ChatPanel │ Sidebar │ GraphPanel │ KnowledgePanel   │
│  ToolsPanel │ LoginView │ SettingsView │ AdminView   │
└──────────────────┬──────────────────────────────────┘
                   │ SSE / REST
          ┌────────▼────────┐
          │  Bucket4j 限流   │  30 tokens / 1/s refill
          │  JWT 鉴权 Filter │  USER / ADMIN
          └────────┬────────┘
┌──────────────────▼──────────────────────────────────┐
│              Spring Boot 4.1 (Java 21)               │
│                                                      │
│  ChatController ──→ ChatService (Agent 核心)         │
│                        │                             │
│     ┌──────────────────┼──────────────────┐          │
│     ▼         ▼         ▼         ▼        ▼         │
│   Redis     @Tool     Qdrant    Neo4j   DeepSeekApi  │
│   Memory    18工具    向量检索   概念关系  (Spring AI) │
│                                                      │
│  AuthController  ConversationController              │
│  KnowledgeController  GraphController                │
│  ToolController  TodoController  AdminController     │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────┐
│  MySQL 8  │  Redis 7  │  Qdrant  │  Neo4j 5  │  OSS  │
└─────────────────────────────────────────────────────┘
```

---

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 框架 | Spring Boot 4.1.0 | Java 21, Virtual Threads |
| AI | Spring AI 2.0 + DeepSeekApi | reasoning_content 原生支持 |
| 模型 | DeepSeek v4-flash | 默认思考模式，工具调用 |
| 嵌入 | BAAI/bge-m3 (硅基流动) | 1024 维 |
| 前端 | Vue 3 + Vite 8 | SFC 组件 + vue-router SPA |
| 图谱可视化 | ECharts + vue-echarts | 层次化树形图（LR） |
| Markdown | marked | 消息渲染 |
| 关系库 | MySQL 8 + MyBatis-Plus | 用户/会话/消息/文档/待办 |
| 缓存 | Redis 7 + Lettuce | ChatMemory + 上下文摘要 |
| 向量库 | Qdrant | 语义检索 |
| 图数据库 | Neo4j 5 | 概念关系 + 路径查询 |
| 对象存储 | 阿里云 OSS | 文档 + 头像 |
| 鉴权 | JWT Filter + BCrypt | 无状态认证 |
| 限流 | Bucket4j | 令牌桶（30cap, 1/s refill） |

---

## 快速开始

### 环境要求

- Java 21+
- MySQL 8+
- Redis 7+
- Neo4j 5+
- Qdrant
- Node.js 20+（前端开发）

### 1. 启动基础设施

```bash
# MySQL, Redis, Neo4j, Qdrant 需要预先运行
# Qdrant collection 需手动创建:
curl -X PUT http://localhost:6333/collections/jiang_i_agent_knowledge \
  -H 'Content-Type: application/json' \
  -d '{"vectors":{"size":1024,"distance":"Cosine"}}'
```

### 2. 初始化数据库

```bash
# 全新安装
mysql -u root < src/main/resources/sql/schema.sql

# 如果从旧版本升级 (thinking 字段)
mysql -u root jiang_i_agent < src/main/resources/sql/migration_add_thinking.sql
```

### 3. 配置

编辑 `src/main/resources/application-dev.yml`，填写：
- DeepSeek API Key
- 硅基流动 API Key（embedding）
- MySQL / Redis / Neo4j / Qdrant 连接信息
- 阿里云 OSS AccessKey

### 4. 启动

```bash
# 后端 (默认 8080 端口)
./mvnw spring-boot:run

# 前端开发服务器 (可选，默认 5173 端口)
cd frontend && npm install && npm run dev

# 生产构建 (输出到 src/main/resources/static/)
cd frontend && npm run build
```

### 5. 访问

- 浏览器打开 `http://localhost:8080`
- 注册账号后登录（首次注册的用户可手动升级为 ADMIN）

---

## 项目结构

```
jiang_I-agent/
├── src/main/java/com/jiang/
│   ├── common/              # Result 统一响应 / 异常处理
│   ├── config/              # Redis / MyBatis-Plus / Web / OSS 配置
│   ├── controller/          # REST 控制器 (10 个)
│   ├── entity/              # MySQL 实体
│   ├── mapper/              # MyBatis-Plus Mapper
│   ├── model/
│   │   ├── req/             # 请求 DTO
│   │   └── vo/              # 视图对象 (MessageVO, PageResult)
│   ├── repository/          # Neo4j Repository
│   ├── service/             # 业务服务
│   │   └── ChatService.java # Agent 核心 (773行)
│   └── tool/                # @Tool 注解 + ToolRegistry + 18 个工具实现
├── src/main/resources/
│   ├── prompts/system.md    # Agent 系统提示词
│   ├── sql/
│   │   ├── schema.sql       # 全量表结构
│   │   └── migration_add_thinking.sql  # thinking 字段迁移
│   ├── static/              # 前端构建产物 (Vite build 输出)
│   └── application*.yml     # 配置
├── frontend/                # Vue 3 前端项目
│   └── src/
│       ├── App.vue          # 主布局
│       ├── router.js        # 路由 + 守卫
│       ├── stores/state.js  # 全局状态 + logout
│       ├── assets/style.css # 设计系统 (30KB)
│       ├── utils/           # api.js / chat.js / toast.js / storage.ts
│       ├── components/      # 5 个面板组件
│       └── views/           # 3 个页面视图
├── md/                      # 项目文档
│   ├── PROJECT.md           # 项目总览
│   ├── DESIGN.md            # 技术设计
│   ├── API_DESIGN.md        # 接口文档
│   └── ISSUES.md            # 踩坑记录
└── pom.xml
```

---

## 前端组件

| 组件 | 功能 |
|------|------|
| `ChatPanel.vue` | SSE 流式聊天：thinking/content/tool_call 事件分派、思考框折叠、打字机光标、marked 渲染 |
| `Sidebar.vue` | 会话列表 + 批量删除 + 登出按钮 + 设置/管理入口（SVG 图标） |
| `GraphPanel.vue` | Neo4j 图谱：ECharts 层次化树形图 + 关系过滤 + 概念删除 + 搜索/分页/路径查询 + Teleport 模态框 |
| `KnowledgePanel.vue` | RAG 知识库：文档上传/搜索/列表/删除/下载（SVG 文件图标） |
| `ToolsPanel.vue` | 工具标签卡片（名称+描述）+ 待办 checkbox CRUD |
| `LoginView.vue` | 登录/注册切换、JWT 存储 |
| `SettingsView.vue` | 头像上传、昵称、密码修改 |
| `AdminView.vue` | 用户管理表格 + Agent 全局配置（名称/模型/温度/提示词） |

---

## 工具清单

| 类别 | 工具 | 数量 |
|------|------|------|
| 待办 | `create_todo` `list_todos` `complete_todo` `delete_todo` | 4 |
| 提醒 | `create_reminder` `list_reminders` `cancel_reminder` | 3 |
| 知识库 | `search_knowledge` `list_knowledge` | 2 |
| 图谱 | `search_concepts` `find_learning_path` `add_concept` | 3 |
| 时间 | `get_current_time` | 1 |
| 网络 | `read_web_page` `search_web` | 2 |
| 对话 | `search_conversation` `export_conversation` | 2 |
| 系统 | `get_status` | 1 |

---

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/chat` | 同步对话 |
| GET/POST | `/api/chat/stream` | SSE 流式对话（POST 支持附件，?thinking=true 开启思考） |
| POST | `/api/chat/upload` | 上传对话附件（Tika 解析返回文本） |
| POST | `/api/knowledge/documents` | 上传文档 |
| GET | `/api/knowledge/documents` | 文档列表 |
| DELETE | `/api/knowledge/documents/{id}` | 删除文档 |
| GET | `/api/knowledge/documents/{id}/download` | 下载文档 |
| POST | `/api/knowledge/search` | RAG 问答 |
| GET | `/api/graph/concepts` | 概念搜索 |
| GET | `/api/graph/concepts/{name}` | 概念详情 |
| GET | `/api/graph/concepts/{name}/path` | 知识链查询 |
| GET | `/api/graph/concepts/{name}/graph` | 子图 (ECharts) |
| POST | `/api/graph/concepts` | 添加概念 |
| DELETE | `/api/graph/concepts/{name}` | 删除概念（级联） |
| DELETE | `/api/graph/concepts/{name}/relations` | 删除关系 |
| GET | `/api/tools` | 工具列表 |
| GET/POST/PUT/DELETE | `/api/todos[/{id}/complete]` | 待办 CRUD |
| GET | `/api/conversations` | 会话列表 |
| GET | `/api/conversations/{id}/messages` | 消息列表 (含 thinking) |
| DELETE | `/api/conversations/{id}` | 删除会话 |
| POST | `/api/conversations/batch-delete` | 批量删除 |
| GET/PUT | `/api/admin/agent` | Agent 配置 |
| GET/DELETE | `/api/admin/users/*` | 用户管理 |
| GET/POST/PUT | `/api/profile/*` | 个人设置 |

完整接口文档见 [API_DESIGN.md](md/API_DESIGN.md)。

---

## 设计系统

前端使用完整 CSS 自定义属性体系：

| Token | 值 | 用途 |
|-------|-----|------|
| `--accent` | `#F472B6` | 主色（淡粉） |
| `--accent-deep` | `#EC4899` | 渐变暗端 |
| `--accent-light` | `#FBCFE8` | 卡片背景 |
| `--lavender` | `#8B5CF6` | 紫色辅助（思考框） |
| `--color-error` | `#EF4444` | 错误/删除 |
| `--color-success` | `#22C55E` | 成功 |
| `--text-primary` | `#1E293B` | 主文字 |
| `--bg-body` | `#FDF2F8` | 页面背景 |

---

## 文档

| 文档 | 内容 |
|------|------|
| [PROJECT.md](md/PROJECT.md) | 项目总览、架构、分阶段路线 |
| [DESIGN.md](md/DESIGN.md) | 数据库设计、Neo4j 模型、Redis 键、鉴权、前端架构 |
| [API_DESIGN.md](md/API_DESIGN.md) | 完整接口文档（含 SSE 协议） |
| [ISSUES.md](md/ISSUES.md) | 23 条踩坑记录 + 教训总结 |
| [system.md](src/main/resources/prompts/system.md) | Agent 系统提示词 |

---

## License

MIT
