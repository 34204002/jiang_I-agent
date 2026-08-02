# Jiang I-Agent Frontend

Vue 3 + Vite SPA。组件化聊天界面，SSE 流式（fetch + ReadableStream）+ 拖拽文件上传（Agent Tool 模式）+ 知识图谱可视化 + 知识库管理 + 工具面板 + 定时提醒。

## 技术栈

- Vue 3 (Composition API + `<script setup>`)
- Vite 8
- vue-router 5
- marked + DOMPurify (Markdown 渲染 + XSS 防护)
- ECharts + vue-echarts (图谱可视化)
- VueUse (useStorage)

## 开发

```bash
npm install
npm run dev       # http://localhost:5173
```

## 构建

```bash
npm run build     # 输出到 ../src/main/resources/static/
```

## 项目结构

```
src/
├── App.vue                    # 主布局: Sidebar + 4 Tab + router-view
├── main.ts                    # 入口: createApp + router + 全局样式
├── router.ts                  # /login /chat /settings /admin + 导航守卫
├── stores/
│   ├── state.ts               # reactive 全局状态 + loadAgentConfig + logout
│   └── chat.ts                # 会话加载/选择/创建
├── assets/style.css           # 全局设计系统 (CSS 自定义属性, 粉蓝双色)
├── utils/
│   ├── api.ts                 # HTTP 客户端 (JWT auth, 统一错误处理)
│   ├── helpers.ts             # 工具函数 (safeJsonParse)
│   ├── toast.ts               # Toast 通知
│   ├── storage.ts             # localStorage 封装 (VueUse useStorage)
│   └── reminders.ts           # 定时提醒轮询（到期弹通知 + ack）
├── components/
│   ├── ChatPanel.vue          # SSE 流式聊天 + 拖拽文件上传
│   ├── Sidebar.vue            # 会话列表 + 登出
│   ├── GraphPanel.vue         # Neo4j 图谱 (ECharts)
│   ├── KnowledgePanel.vue     # RAG 知识库管理
│   └── ToolsPanel.vue         # 工具列表 + 待办
└── views/
    ├── LoginView.vue          # 登录/注册
    ├── SettingsView.vue       # 个人设置
    └── AdminView.vue          # 管理后台
```

## SSE 协议

流式端点 `POST /api/chat/stream` 返回 `text/event-stream`，每个 data 块为 JSON，**首帧必为 `conversation_id`**：

```json
{"type":"conversation_id","id":"12"}
{"type":"thinking","content":"思考过程..."}
{"type":"content","content":"回复正文"}
{"type":"tool_call","name":"search_knowledge","args":"{...}"}
```

| type | 前端行为 |
|------|---------|
| `conversation_id` | 新建会话时由此获取 ID |
| `thinking` | 追加到思考框（流式结束自动折叠） |
| `content` | 追加到气泡（打字机光标） |
| `tool_call` | 更新思考标题，清空 content 暂存 |

传输用 `fetch` + `ReadableStream` 手写 SSE（非 EventSource）：JWT 走 `Authorization` 头不进 URL，HTTP 非 200 立即失败不重连，`AbortController` 支持取消。详见根目录 `md/ISSUES.md` §25-26。

## 设计主题

全局 CSS 自定义属性（`src/assets/style.css`）粉蓝双色：用户气泡淡粉 `#FEF2F7`、AI 气泡淡天蓝 `#EFF8FF`，主操作粉色渐变、次要强调天蓝（链接/outline 按钮），背景粉→蓝渐变。主色 token：`--accent #F472B6` / `--sky #38BDF8`。
