# Jiang I-Agent Frontend

Vue 3 + Vite SPA。组件化聊天界面，SSE 流式 + 拖拽文件上传 + 知识图谱可视化 + 知识库管理 + 工具面板。

## 技术栈

- Vue 3 (Composition API + `<script setup>`)
- Vite 8
- vue-router 5
- marked (Markdown 渲染)
- ECharts + vue-echarts (图谱可视化)
- VueUse (useStorage / useEventSource / useTemplateRef)
- DOMPurify (XSS 防护)

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
├── main.js                    # 入口: createApp + router + 全局样式
├── router.js                  # /login /chat /settings /admin + 导航守卫
├── stores/state.js            # reactive 全局状态 + loadAgentConfig + logout
├── assets/style.css           # 全局设计系统 (30KB CSS 自定义属性)
├── utils/
│   ├── api.js                 # HTTP 客户端 (JWT auth, 统一错误处理)
│   ├── chat.js                # 会话加载/选择/创建
│   ├── toast.js               # Toast 通知
│   └── storage.ts             # localStorage 封装 (VueUse useStorage)
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

流式端点返回 `text/event-stream`，每个 data 块为 JSON：

```json
{"type":"thinking","content":"思考过程..."}
{"type":"content","content":"回复正文"}
{"type":"tool_call","name":"search_knowledge","args":"{...}"}
```
