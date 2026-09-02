<div align="center">

# Jiang I-Agent 🤖

**个人 AI 知识库助手 · 单智能体 Agent 全栈实现**

Spring Boot 4.1 · Spring AI 2.0 · DeepSeek v4-flash · Neo4j 5 · Qdrant · MySQL 8 · Redis 7 · Vue 3

*RAG 向量检索 + Neo4j 知识图谱双检索 · 自研 @Tool 工具调度 · SSE 流式思考可视化*

</div>

---

## 🎬 演示

📺 [**项目演示视频（Bilibili）**](https://www.bilibili.com/video/BV17F7W6GEnM/?spm_id_from=333.1387.homepage.video_card.click)

![Jiang I-Agent 截图](https://jiang-learning.oss-cn-beijing.aliyuncs.com/Snipaste_2026-06-27_16-24-58.png)

---

## ✨ 项目亮点

- 🧠 **深度适配 DeepSeek 流式推理**：解决 OpenAI 适配器丢失 `reasoning_content`、并行工具参数粘连触发 DSML 标签、内容缓冲破坏打字机效果三大问题；工具调度轮 `reasoning_content` 不分模式**原样回传**（压测发现并修复非思考模式工具调用 400）。单请求首字实测均值 **395ms**（DeepSeek 接口 5 次采样）；8 并发压测下 SSE 会话准备 **≈310ms**（稳定）、端到端全量 p95 ≈ **7.6s**
- 📐 **耗时可观测 + 压测脚本**：`[SSE_FRAME]`/`[TTFT]`/`[RAG_TIME]`/`[TOOL_TIME]` 四处 nanoTime 埋点，`scripts/load-test.sh` 并发对话自动收敛各指标 p50/p95，所有数字可复现
- 🔧 **自研 `@Tool` 注解框架**：脱离 Spring AI ChatClient 链路，应用就绪后容器扫描注册 + 反射执行 + ThreadLocal 上下文传递，支撑 **19 个 @Tool 工具方法（10 个工具类）**自主调用、最多 10 轮循环（MAX_TOOL_ROUNDS），单轮多 tool_calls 按 index 分组累积
- 📚 **RAG + 图谱双检索体系**：Qdrant 语义检索 + Neo4j 前置知识链查询（"学 Redis 前先学什么"），纯向量检索做不到的知识结构
- 🔒 **全链路工程化治理**：JWT 无状态鉴权、Bucket4j 限流、Redis 会话记忆、事务保障，知识库/图谱**按用户隔离**
- 🎨 **完整前端设计系统**：Vue 3 SPA + CSS 自定义属性淡蓝色主题、思考框折叠、流式打字机、图谱层次化可视化

---

## 🏗️ 架构总览

```
┌──────────────────────────────────────────────────────────────┐
│                        浏览器 (Vue 3 SPA)                       │
│  ChatPanel │ Sidebar │ GraphPanel │ KnowledgePanel │ ToolsPanel │
└──────────────────────────┬───────────────────────────────────┘
                           │ SSE (fetch+ReadableStream) / REST
              ┌────────────▼────────────┐
              │   RateLimitInterceptor   │  Bucket4j 令牌桶
              │   LoginInterceptor       │  JWT Bearer 鉴权
              └────────────┬────────────┘
┌──────────────────────────▼──────────────────────────────────┐
│                   Spring Boot 4.1 (Java 21)                  │
│                                                              │
│   ChatController ──→ ChatService (Agent 核心, 1071 行)          │
│                       │                                      │
│      ┌──────────┬─────┴──────┬──────────┬─────────────┐      │
│      ▼          ▼            ▼          ▼             ▼      │
│   Redis     @Tool 19 个    Qdrant     Neo4j        DeepSeek  │
│   Memory   ToolRegistry   向量检索    概念/关系    (Spring AI) │
│                                                              │
│   Knowledge(RabbitMQ异步) │ Graph/Todo/Reminder/Conversation/Admin
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  MySQL 8 │ Redis 7 │ Qdrant │ Neo4j 5 │ RabbitMQ │ 阿里云 OSS │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 核心特性

### 💬 对话（Agent 核心）
- **SSE 流式输出**：`fetch` + `ReadableStream` 手写 SSE（非 EventSource），逐字打字机效果，防无限重连
- **DeepSeek thinking 可视化**：思考过程独立事件推送，思考框自动折叠 + chevron 切换
- **多轮对话**：Redis ChatMemory 30min TTL + MySQL 持久化 + 上下文摘要自动压缩
- **拖拽文件上传**：PDF/MD/TXT/DOCX → Tika 解析 → LLM 经 `read_uploaded_file` 按需读取

### 🔧 Agent 工具（19 个）
| 类别 | 工具 | 说明 |
|------|------|------|
| 文件 | `read_uploaded_file` | 按 fileId 读取对话附件 |
| 待办 | `create/list/complete/delete_todo` | 全生命周期管理 |
| 提醒 | `create/list/cancel_reminder` | 定时提醒，前端轮询到点弹通知 |
| 知识库 | `search_knowledge` `list_knowledge` | Qdrant 语义检索 + 文档列表 |
| 图谱 | `search_concepts` `find_learning_path` `add_concept` | 搜索/路径/沉淀 |
| 时间/网络 | `get_current_time` `read_web_page` `search_web` | 联网能力 |
| 对话/系统 | `search_conversation` `export_conversation` `get_status` | 历史/导出/状态 |

### 📚 RAG 知识库（RabbitMQ 异步化）
- **受理即返回**：上传只做 校验/SHA-256 去重/OSS 落盘/入库(status=0)/投递 MQ 消息，**0.5s 内响应**
- **后台异步处理**：消费者从 OSS 取回字节（队列只传 `{docId}` 凭证不传文件）→ Tika 解析 → 分块 → **bge-m3 向量化** → Qdrant，状态机 `0处理中→1已解析→2已向量化`
- **失败可见可自愈**：重试 3 次指数退避 → 死信队列 → 标 `status=3(失败)+原因`，前端轮询红字展示；失败重传自动复用原行重开一轮
- SHA-256 去重（按用户，仅 status=2 算重复）+ 阿里云 OSS 存储 + 下载
- 检索后按 MySQL 归属**二次过滤**（post-filter），支持存量数据

### 🕸️ 知识图谱（差异化能力）
- Neo4j 概念建模：`PREREQUISITE_OF`（前置）+ `RELATED_TO`（相关）双关系
- **知识链查询**："学 Redis 前需要先学什么" → 1~N 跳前置路径
- 三层防护：**循环检测 + 自环预防 + 传递化简**（防止 AI 自主维护导致图谱杂乱）
- ECharts 层次化树形图：关系过滤（仅前置/仅相关/全部）、双击加载邻居
- AI 对话自动沉淀概念，手动添加/编辑/删除

### 🎨 前端设计系统
- Vue 3 SFC + vue-router SPA，全局 CSS 自定义属性 token
- 淡蓝色主题：用户气泡深蓝、AI 气泡浅蓝，主操作与次要强调均为天蓝系（主色 `#0284C7` 白字对比达 WCAG AA），背景淡蓝渐变
- SVG 图标（无 emoji）、响应式、WCAG 无障碍、`prefers-reduced-motion`

| Token | 值 | 用途 |
|-------|-----|------|
| `--accent` / `--accent-deep` | `#0284C7` / `#0369A1` | 主色（天蓝渐变，主按钮） |
| `--sky` / `--sky-deep` | `#38BDF8` / `#0EA5E9` | 次色（天蓝，链接/outline 按钮/图谱前置边） |
| `--lavender` | `#8B5CF6` | 紫色辅助（思考框） |
| `--ai-bubble` / `--user-bubble` | `#F0F9FF` / `#DBEAFE` | AI 浅蓝 / 用户深蓝气泡 |
| `--color-error` / `--color-success` / `--color-warning` | `#EF4444` / `#22C55E` / `#F59E0B` | 语义色 |
| `--text-primary` / `--bg-body` | `#1E293B` / `#F0F9FF` | 主文字 / 页面背景（淡蓝渐变） |

### 🔐 安全与工程化
- JWT 无状态认证（token 只走 header，不进 URL）+ BCrypt 密码
- Bucket4j 令牌桶限流（30 tokens，~60 req/min/用户）
- 知识库文档 / 图谱概念**按用户隔离**，越权删除/访问 403
- 修改密码（校验旧密码）、角色隔离 USER / ADMIN

---

## 🛠️ 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot (WebMVC) | 4.1.0 |
| AI | Spring AI + spring-ai-deepseek | 2.0.0 |
| 模型 | DeepSeek v4-flash | 思考模式可开关（工具轮恒返 reasoning_content） |
| 嵌入 | BAAI/bge-m3（硅基流动） | 1024 维 |
| 前端 | Vue 3 + Vite | 3.5 / 8.x |
| 图谱可视化 | ECharts + vue-echarts | 5.6 / 7.0 |
| 关系库 | MySQL 8 + MyBatis-Plus | — |
| 缓存 | Redis 7 + Lettuce | — |
| 向量库 | Qdrant | Cosine |
| 图数据库 | Neo4j 5 | Cypher |
| 异步消息 | RabbitMQ + Spring AMQP | 4.1 |
| 对象存储 | 阿里云 OSS | — |
| Markdown | marked + DOMPurify | XSS 防护 |

---

## ⚡ 快速开始

### 环境要求
Java 21+ · MySQL 8+ · Redis 7+ · Neo4j 5+ · Qdrant · RabbitMQ 3+ · Node.js 20+（前端开发）

### 1. 启动基础设施
```bash
# MySQL(3306) + Redis(6379) + Neo4j(7687) + Qdrant(6334) + RabbitMQ(5672/15672) 需预先运行
# RabbitMQ 队列拓扑由应用启动时自动声明（RabbitConfig），无需手动建
# Qdrant collection 手动创建（Spring AI 不自动建）：
curl -X PUT http://localhost:6333/collections/jiang_i_agent_knowledge \
  -H 'Content-Type: application/json' \
  -d '{"vectors":{"size":1024,"distance":"Cosine"}}'
```

### 2. 初始化数据库
```bash
# 全新安装
mysql -u root < src/main/resources/sql/schema.sql

# 从旧版升级（知识库/图谱用户隔离，存量数据归最早用户）
mysql -u root jiang_i_agent < src/main/resources/sql/migration_add_user_isolation.sql
# Neo4j 存量概念归属最早用户（替换 <OLDEST_USER_ID>）：
#   MATCH (c:Concept) WHERE c.userId IS NULL SET c.userId = <OLDEST_USER_ID>

# 从旧版升级（模型默认值统一为 deepseek-v4-flash）
mysql -u root jiang_i_agent < src/main/resources/sql/migration_update_model.sql
```

### 3. 配置
编辑 `src/main/resources/application-dev.yml`，填写 DeepSeek / 硅基流动 / MySQL / Redis / Neo4j / Qdrant / OSS 连接信息。

### 4. 启动
```bash
./mvnw spring-boot:run          # 后端 http://localhost:8080

cd frontend && npm install && npm run dev   # 前端开发 http://localhost:5173
cd frontend && npm run build     # 生产构建 → src/main/resources/static/
```

### 5. 访问
浏览器打开 `http://localhost:8080`，注册账号后登录（首次注册的用户可手动升级为 ADMIN）。

---

## 📂 项目结构

```
jiang_I-agent/
├── src/main/java/com/jiang/
│   ├── config/           # Redis / MyBatis-Plus / Web / OSS / RabbitMQ / 限流 / 鉴权配置
│   ├── controller/       # REST 控制器（Chat/Knowledge/Graph/Todo/Reminder/...）
│   ├── service/          # 业务服务（ChatService = Agent 核心, 1071 行）
│   ├── mq/               # RabbitMQ 消费者（VectorizeConsumer + DlqConsumer 死信收尾）
│   ├── tool/             # @Tool 注解 + ToolRegistry + ToolContext + 19 个工具
│   ├── repository/       # Neo4j Repository
│   ├── entity/ mapper/ model/ common/ util/ exception/
├── src/main/resources/
│   ├── prompts/system.md # Agent 系统提示词
│   ├── sql/              # schema.sql + 迁移脚本
│   ├── static/           # 前端构建产物
│   └── application*.yml
├── frontend/             # Vue 3 SPA（router/stores/utils/assets/components/views）
└── md/                   # 项目文档（详见下方）
```

### 前端组件

| 组件 | 功能 |
|------|------|
| `ChatPanel.vue` | SSE 流式聊天：`conversation_id`/`thinking`/`content`/`tool_call` 事件分派、思考框折叠、打字机光标、marked 渲染 |
| `Sidebar.vue` | 会话列表 + 批量删除 + 登出按钮 + 设置/管理入口（SVG 图标） |
| `GraphPanel.vue` | Neo4j 图谱：ECharts 层次化树形图 + 关系过滤 + 概念删除 + 搜索/分页/路径查询 + Teleport 模态框 |
| `KnowledgePanel.vue` | RAG 知识库：异步上传轮询（处理中/完成/失败红字）、搜索/列表/删除/下载 |
| `ToolsPanel.vue` | 工具标签卡片（名称+描述）+ 待办 checkbox CRUD |
| `LoginView.vue` | 登录/注册切换（密码显隐 + 注册确认密码）、JWT 存储 |
| `SettingsView.vue` | 头像上传、昵称、密码修改、对话模型 BYOK（自带 DeepSeek key/选模型） |
| `AdminView.vue` | 用户管理表格 + Agent 全局配置（名称/模型/温度/提示词） |

---

## 📚 文档

| 文档 | 内容 |
|------|------|
| [README.md](README.md) | 本项目总览 |
| [md/PROJECT.md](md/PROJECT.md) | 项目定位、架构、分阶段路线 |
| [md/DESIGN.md](md/DESIGN.md) | 版本选型、数据库设计、Neo4j 模型、Redis 键、鉴权、SSE 协议 |
| [md/API_DESIGN.md](md/API_DESIGN.md) | 完整接口文档（含 SSE 协议、用户隔离说明） |
| [md/ISSUES.md](md/ISSUES.md) | 32 条踩坑记录 + 16 条工程教训 |
| [system.md](src/main/resources/prompts/system.md) | Agent 系统提示词 |

---

## 🔌 API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` `/api/auth/register` | 登录 / 注册 |
| POST | `/api/chat/stream` | SSE 流式对话（`?thinking=true`，首帧 `conversation_id`） |
| POST | `/api/chat` | 同步对话 |
| POST | `/api/chat/upload` | 上传对话附件（Tika 解析，返回 fileId） |
| POST/GET/DELETE | `/api/knowledge/documents*` | 文档异步上传（受理秒回）/ 列表 / 删除 / 下载 |
| GET | `/api/knowledge/documents/{id}/status` | 文档处理状态轮询（0处理中/2完成/3失败） |
| POST | `/api/knowledge/search` | RAG 知识库问答 |
| GET | `/api/graph/concepts*` | 概念搜索 / 分类 / 详情 / 知识链 / 子图 |
| POST/DELETE | `/api/graph/concepts*` | 添加 / 删除概念、关系 |
| GET | `/api/tools` | 工具列表 |
| CRUD | `/api/todos*` `/api/reminders*` | 待办 / 提醒（轮询 + ack） |
| GET/DELETE | `/api/conversations*` | 会话 / 消息 / 批量删除 |
| GET/PUT | `/api/admin/agent` `/api/admin/users*` | 管理后台 |
| GET/POST/PUT | `/api/profile/*` | 个人设置 / 头像 / 修改密码 |

完整接口文档见 [md/API_DESIGN.md](md/API_DESIGN.md)。

---

## 🧠 关键工程决策（面试可聊）
1. **为什么脱离 Spring AI ChatClient？** 官方适配器丢失 DeepSeek 非标准字段、工具编排不够透明。保留 `spring-ai-deepseek` 做**类型解析**，HTTP 请求和工具编排**自己控制**——类型安全但不失灵活性。

2. **为什么不用 EventSource？**
   EventSource 无法自定义 header（JWT 只能进 URL）、错误后自动重连导致死循环。改用 `fetch` + `ReadableStream` 手写 SSE，HTTP 状态码非 200 立即失败。

3. **ThreadLocal 跨线程问题怎么解？**
   Reactor 调度线程上 ThreadLocal 不可见 → 工具执行 401/串会话。`ToolContext.runWithContext()` 显式把 userId/convoId 传进执行线程，finally 恢复。

4. **图谱为什么有三层防护？**
   AI 自主维护图谱会失控（循环、自环、冗余边）。后端强制循环检测 + 自环预防 + 传递化简，提示词收紧"仅在新且重要时沉淀"。

5. **工具调用的 `reasoning_content` 为什么不分模式都要回传？**
   8 并发压测发现的 400：`deepseek-v4-flash` 在**非思考模式**的工具规划轮照样返回 `reasoning_content`，DeepSeek 强制要求 tool_calls 轮把它**原样带回**，否则拒绝。修复后始终捕获并随工具消息回传，思考模式开关只决定"要不要展示"。

6. **文档上传为什么要异步化（RabbitMQ）？**
   上传链路的重活（Tika 解析、远程向量化）会占住 HTTP 线程数十秒；且文件字节**跨不过消息队列**（MultipartFile 随请求销毁）。设计：OSS 上传作持久化 commit 点 → 队列只传 `{docId}` 凭证 → 消费者回 OSS 取回字节后台处理。状态机 + 死信队列让失败**可见且可重传自愈**，不留僵尸文档。

---

## 📄 License

MIT
