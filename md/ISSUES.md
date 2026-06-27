# Jiang I-Agent 开发踩坑记录

## 1. reasoning_content 丢失

**症状**：DeepSeek 思考模式流式输出时 `reasoning_content` 丢失，同步调用正常。

**根因**：Spring AI 2.0 底层使用 OpenAI 官方 Java SDK，SDK 对象模型不含 `reasoning_content` 非标准字段，直接丢弃。

**修复**：切换到 `spring-ai-deepseek` 专用模块，`ChatCompletionMessage.reasoningContent()` 原生支持。

---

## 2. DSML 格式函数调用 + 根因定位

**症状**：DeepSeek 在 `delta.content` 输出自创标签格式工具调用：
```xml
<tool_calls>
<invoke name="add_concept"><parameter name="name">Redis</parameter></invoke>
</tool_calls>
```

**最终根因**（2026-06-26 定位）：**流式代码并行 tool_calls 参数粘连导致工具执行失败，模型 fallback 到 DSML。**

**修复**：按 `delta.tool_calls[].index` 分组独立累积参数（`Map<Integer,StringBuilder>`），工具执行成功后 DSML 自然消失。

**仅剩防御**：content 流中过滤 `<tool_calls>` `<invoke ` `</tool_calls>` 标签。

---

## 3. 自建 Tool 体系

**为什么不用 Spring AI 的 @Tool？**

决策链：
1. DeepSeek 的 `reasoning_content` 是 OpenAI 非标准字段
2. Spring AI 2.0 OpenAI 适配器底层用 OpenAI Java SDK → 流式时直接丢弃 `reasoning_content`
3. 切换到 `spring-ai-deepseek` 模块，`ChatCompletionMessage.reasoningContent()` 原生支持
4. 但 DeepSeek 的 function calling 行为与 OpenAI 有差异（如并行 tool_calls、DSML fallback），Spring AI 的 `ChatClient` 抽象层无法精细控制
5. **最终决策**：用 Spring AI 的 `ChatCompletionChunk` 类型做响应解析（类型安全），但自建 `buildRequestBody()` 控制请求构建和工具编排
6. 脱离 `ChatClient` → `ChatModel` → `ToolCallback` 链路后，Spring AI 的 `@Tool` 注解自然不可用

**自建方案：**

| 组件 | 职责 |
|------|------|
| `@Tool` 注解 | name + description + JSON Schema |
| `ToolRegistry` | `ApplicationReadyEvent` 时扫描所有 `@Component` Bean、运行时反射执行 |
| `ToolContext` | ThreadLocal 传递 userId/convoId/reasoningContent |

> 核心思想：**用框架的类型解析，保留自己的编排控制**。类型安全但不失灵活性。

现注册 **18 个工具**，最多 **10 轮**工具循环。schema 规范：所有工具含 `"type":"object"` + `"required"` 字段（即使为空数组）。

---

## 4. ToolRegistry 循环依赖

**症状**：`ApplicationContextAware.setApplicationContext()` 中 `ctx.getBean()` 触发未就绪 bean → `BeanCurrentlyInCreationException`。

**修复**：改为 `@EventListener(ApplicationReadyEvent.class)`。

---

## 5. Jackson DefaultTyping 污染 API 请求

**症状**：RedisConfig 的 ObjectMapper 开 `activateDefaultTyping`，注入 ChatService 后序列化 JSON 带 Jackson 类型注解 → API 400。

**修复**：API 请求用 `new ObjectMapper()` 干净实例。

---

## 6. Flux.create() 流式阻塞

**症状**：SSE 内容积压到 API 结束后一次性吐出。**修复**：换 `HttpClient.sendAsync()` + `BodyHandlers.ofLines()` + `Flux.fromStream()`。

---

## 7. Spring Boot 4.1 包名变更

`RestClientCustomizer` 从 `org.springframework.boot.web.client` → `org.springframework.boot.restclient`。

---

## 8. 硅基流动 Embedding 模型

`text-embedding-3-small` 是 OpenAI 模型，硅基流动不支持。换为 `BAAI/bge-m3`（1024 维）。

---

## 9. Qdrant Collection 手动创建

Spring AI 不自动建 collection。Qdrant gRPC（6334）和 REST（6333）端口不同。

---

## 10. 前端 SSE 流式问题（vanilla JS 时期）

- 跨消息串流：`#streamBubble` id 未清理 → `onSend()` 开头清旧 id
- 打字机被杀死：`es.onerror` 中 `clearInterval` 丢残留文本
- 思考块重复：`es.onerror` 用 `innerHTML` 新建 block 与原有共存

---

## 11. DeepSeekStreamService 替换

用 Spring AI `spring-ai-deepseek` 的 `ChatCompletionMessage`/`ChatCompletionChunk` 替代手动 JsonNode 解析。保留自研 `buildRequestBody()` 和工具编排。

---

## 12. v4-flash 默认思考模式

`deepseek-v4-flash` 不传 `thinking: {type: "enabled"}` 也返回 `reasoning_content`——模型默认开启思考。

---

## 13. 并行 tool_calls 参数粘连（DSML 根因）

用 `Map<Integer, String> tcNames` + `Map<Integer, StringBuilder> tcArgsMap` 按 index 独立累积。

---

## 14. Content 缓冲破坏打字机效果 ★

**症状**（2026-06-26）：修复 DSML 后，前端内容一次性闪现，无逐字打字机效果。

**根因**：`ChatService.streamWithPossibleTools()` 和 `streamToolFollowup()` 中 content chunk 只 append 到 `contentBuf`，注释写明"不立即发射——缓冲到流结束"。然后在 `.concatWith(Flux.defer(...))` 中一次性 `Flux.just(content)` 发送整个正文。

**修复**：content 改为实时 `sink.next("type":"content",...)` 逐 chunk 发射，终轮只持久化不重发。前端 `tool_call` 事件时清 `streamContent`。

---

## 15. thinking 字段从 content 中分离 ★

**症状**：思考内容用 `<thinking>...</thinking>` HTML 标签包裹在 content 字段中，marked 渲染时当作 HTML 处理，思考混入正文。

**根因**：Message 实体没有独立 thinking 字段，后端 `saveMessage` 拼接 `<thinking>` + content，前端靠正则解析但 marked 已吞掉标签。

**修复**（全链路）：
- DB: `ALTER TABLE t_message ADD COLUMN thinking MEDIUMTEXT`
- Entity: `Message.thinking` 字段
- VO: `MessageVO.thinking` 字段
- ChatService: `saveMessage()` 加 thinking 参数，不再拼接 `<thinking>` 标签
- 前端: `m.thinking` 直接渲染思考框，`m.content` 渲染正文气泡

---

## 16. Vue 3 SPA 迁移

**背景**：原前端 5 个 vanilla JS 文件（~1630 行），无组件化、无路由、emoji 作图标、innerHTML 拼接 XSS 风险。

**迁移内容**：
- Vite 项目 + 8 个 .vue SFC 组件 + vue-router SPA
- 全局 CSS 设计系统（自定义属性）
- 所有 emoji → SVG（Heroicons 风格）
- getElementById → Vue ref
- alert() → showToast()
- innerHTML v-html → Teleport + 模板渲染（XSS 修复）
- 30+ UI 问题修复（accessibility、contrast、touch target）

---

## 17. Sidebar + Welcome 图标不可见

**症状**：Sidebar 品牌 SVG 和欢迎页 robot SVG 使用 `stroke="currentColor"`，但父元素未设置 `color`，currentColor 回退到 body `#1E293B`（深灰），在粉色→紫色渐变背景上不可见。

**修复**：`.sidebar-brand .icon` 加 `color:#fff`；`.welcome-emoji` 加 `color:var(--accent)`；SVG `stroke-width` `.8` → `1.5`。

---

## 18. 工具列表 API 响应格式不匹配

**症状**：ToolsPanel 显示"加载工具列表…"但实际已拿到数据。

**根因**：后端 `GET /api/tools` → `Result.success(List)`，data 是数组 `[{name,description},...]`。前端检查 `json.data?.tools`（期望 `{tools: [...]}`），永远 undefined。

**修复**：`Array.isArray(json.data) ? json.data : json.data?.tools`。

---

## 19. 输入框样式未应用

**症状**：ChatPanel 的消息输入框显示浏览器默认样式（白底黑框），与粉色设计系统不协调。

**根因**：`.input-row` CSS 子选择器只匹配 `textarea`，模板中使用的是 `<input>`。

**修复**：选择器改为 `.input-row input, .input-row textarea`。

---

## 20. Vue Router 无限重定向

**症状**：`router.replace()` 在组件 `setup()` 阶段调用，与 vue-router 初始化竞争 → 无限循环。

**修复**：移到 `onMounted()` 中执行。

---

## 21. 知识图谱 AI 维护导致杂乱 ★

**症状**：AI 通过 `add_concept` 自由添加概念和关系，长期运行后 RELATED_TO 过量、前置关系不准确、图变成杂乱网状。

**根因**：后端无任何防护——无循环检测、无自环预防、AI 被指示每轮对话都加概念。

**修复**（四管齐下）：
- 后端：`validateRelationship()` 循环检测 + 自环预防 + 传递化简
- 提示词：`add_concept` 从"必须沉淀"收紧为"仅在确实新且重要时"，PREQ 宁缺毋滥，RELATED_TO 限 5 个
- 前端：关系过滤（仅前置/仅相关/全部），默认"仅前置"保持树形干净
- 前端：概念列表 ✕ 删除按钮 → `DELETE /api/graph/concepts/{name}`

---

## 22. 图谱 A→C 插入 B 后需删除冗余边 ★

**症状**：已有 A→C，AI 新加 B 并设置 A→B、B→C 后，A→C 仍存在（传递冗余）。

**根因**：AI 没有全局视图，不知道 A→C 已存在；系统不会自动检测传递闭包。

**修复**：`removeTransitiveRedundancy()` —— 添加 PREQ(A,B) 后自动检测并删除可通过传递推导的冗余边（A→B→C ⇒ 删 A→C）。

---

## 23. 图谱层次化布局换新

**背景**：vis-network 力导向布局（forceAtlas2Based）节点散乱飘动，不适合展示前置知识树。

**改动**：
- 布局换为 `hierarchical { direction:'LR', sortMethod:'directed' }`，physics disabled
- 节点：`box` + `borderRadius:8` + `shadow` + `margin` + `Inter` 字体 + `strokeWidth`
- 边：`cubicBezier` + `forceDirection:'horizontal'`，PREQ 紫色粗线，RELATED_TO 灰色虚线
- 颜色加深 10 色（亮调→暗调），白字 + 描边保证可读性

---

## 教训

1. **非标准字段不信任任何 SDK** — 直接从 HTTP 层验证
2. **用框架的类型，保留自己的控制** — DeepSeekApi 做解析，buildRequestBody 做编排
3. **先验证最底层再往上走** — 别猜，写测试
4. **并行 tool_calls 参数别粘一起** — 按 index 独立累积
5. **空字符串 != null** — 判空永远加 `isEmpty()`
6. **DSML 不是 bug，是症状** — 工具执行失败 → 模型失信 → fallback
7. **流式不要缓冲** — 实时发射维持打字机效果
8. **思考独立存储** — 不要用 HTML 标签包裹在正文中
9. **API 响应格式要对齐** — 前后端数据结构不一致是最常见的 bug
10. **SVG currentColor 必设 color** — 否则继承不可预期的 body 颜色
