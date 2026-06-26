# Jiang I-Agent 开发踩坑记录

## 1. reasoning_content 丢失

**症状**：DeepSeek 思考模式流式输出时 `reasoning_content` 丢失，同步调用正常。

**排查**：

| 轮次 | 判断 | 结论 |
|------|------|------|
| 怀疑 ChunkMerger 丢字段 | 盯错了地方 — reasoning_content 不在 additionalProperties 里 |
| 怀疑用错了 API 层级 | 部分正确 — 应该用 `ChatModel.stream()` 而非 `ChatClient.stream().content()` |
| HTTP 层验证 | 硅基流动原始响应中 `reasoning_content` 完整存在 ✅ |
| Spring AI 层验证 | `metadata["reasoningContent"]` key 存在但值为 `""` ❌ |

**根因**：Spring AI 2.0 底层使用 **OpenAI 官方 Java SDK**，SDK 对象模型不含 `reasoning_content` 这个非标准字段，直接丢弃。

**尝试过的方案**：

| 方案 | 结果 |
|------|------|
| OpenAI 适配器（`spring-ai-starter-model-openai`） | ❌ |
| DeepSeek 原生适配器（`spring-ai-starter-model-deepseek`） | ❌ 开不了思考或同样拿不到 |
| `HttpClient` 直连硅基流动 | ✅ |

**测试代码**：`src/test/java/com/jiang/ReasoningContentRawTest.java`、`SpringAIReasoningTest.java`

---

## 2. DSML 格式函数调用 + 根因定位

**症状**：DeepSeek 在 `delta.content` 里输出自创标签格式的工具调用，不入 `delta.tool_calls`。

```
<tool_calls>
<invoke name="add_concept"><parameter name="name">Redis</parameter></invoke>
</tool_calls>
```

**最终根因**（2026-06-26 定位）：**流式代码并行 tool_calls 参数粘连导致工具执行失败，模型后续 fallback 到 DSML。**

详细链：
1. DeepSeek 在流式模式下并行返回多个 tool_call（如 2 个 `search_knowledge`）
2. 自研流式解析用**单个 `StringBuilder` 累积所有 tool_call 的 arguments**，参数串接成非法 JSON
3. `ToolRegistry.execute()` 参数解析失败 → 返回错误
4. LLM 收到错误后，后续 tool call 放弃标准格式，fallback 到 DSML

**修复**（2026-06-26）：
- 流式解析改为按 `delta.tool_calls[].index` 分组独立累积参数
- `handleToolCallAndContinue` 接收 `List<ToolCall>` 合并处理并行调用
- `buildMessagesWithTools` 构建的 assistant 消息包含所有 tool_calls + 对应 tool 结果

**仅剩的防御**：在 content 流中过滤 `<tool_calls>` `<invoke ` `</tool_calls>` 标签，防止 DSML 泄露到前端。

---

## 3. 自建 Tool 体系

绕过 Spring AI 后，`@Tool` 自动发现/执行体系无法使用，自建：

| 组件 | 职责 |
|------|------|
| `@Tool` 注解 | name + description + JSON Schema |
| `ToolRegistry` | `ApplicationReadyEvent` 时扫描、运行时反射执行 |
| `ToolContext` | ThreadLocal 传递 userId/convoId/reasoningContent |
| `ChatService` | 标准 `delta.tool_calls` 解析 + 工具执行 + 消息注入 + LLM 重调 |

现注册 **17 个工具**（P3 新增 GraphTool 3 个，SystemTool 2 个）。

流程：模型输出 tool_call → 解析执行 → 结果注入 messages → 二次 LLM → 最终回答，最多 5 轮。

**schema 规范**（2026-06-26 统一）：所有工具必须含 `"type":"object"` + `"required"` 字段（即使为空数组），不再依赖 `ToolRegistry` 字符串拼接兜底。

---

## 4. ToolRegistry 循环依赖

**症状**：`@SpringBootTest` 启动失败：
```
BeanCurrentlyInCreationException: chatController → chatService → toolRegistry
  → ctx.getBean(name) → chatController（循环）
```

**根因**：`ApplicationContextAware.setApplicationContext()` 里调 `ctx.getBean()` 触发未就绪 bean。

**修复**：改为 `@EventListener(ApplicationReadyEvent.class)`，上下文完全就绪后再扫描。

---

## 5. Jackson DefaultTyping 污染 API 请求

**症状**：硅基流动 API 返回 400。

**根因**：`RedisConfig` 的 `ObjectMapper` 开了 `activateDefaultTyping`，注入 `ChatService` 后序列化 JSON 带 Jackson 类型注解。

**修复**：API 请求用 `new ObjectMapper()` 干净实例。

---

## 6. Flux.create() 流式阻塞

**症状**：SSE 内容积压到 API 结束后一次性吐出。

**根因**：`Flux.create()` + `subscribeOn(boundedElastic)` 的阻塞 readLine 循环无法背压。

**修复**：换 `HttpClient.sendAsync()` + `BodyHandlers.ofLines()` + `Flux.fromStream()` 惰性拉取。

---

## 7. Spring Boot 4.1 包名变更

`RestClientCustomizer` 从 `org.springframework.boot.web.client` → `org.springframework.boot.restclient`。

---

## 8. 硅基流动 Embedding 模型

`text-embedding-3-small` 是 OpenAI 模型，硅基流不支持。换为 `BAAI/bge-m3`（1024 维）。

---

## 9. Qdrant Collection 手动创建

Spring AI 不自动建 collection。Qdrant gRPC（6334）和 REST（6333）端口不同。
```bash
curl -X PUT http://localhost:6333/collections/jiang_i_agent_knowledge \
  -H 'Content-Type: application/json' \
  -d '{"vectors":{"size":1024,"distance":"Cosine"}}'
```

---

## 10. 前端 SSE 流式问题

- **跨消息串流**：`#streamBubble`/`#thinkingBlock` id 未清理 → `onSend()` 开头清旧 id
- **打字机被杀死**：`es.onerror` 中 `clearInterval` 丢残留文本 → 改用状态标记让 `pumpType()` 自然收尾
- **思考块重复**：`es.onerror` 用 `innerHTML` 新建 block 与原有 `#thinkingBlock` 共存 → 就地更新

---

## 11. DeepSeekStreamService 替换为 Spring AI DeepSeek 类型

**背景**（2026-06-26）：Spring AI 2.0 的 `spring-ai-deepseek` 模块自带 `ChatCompletionMessage` 和 `ChatCompletionChunk`，内置 `reasoningContent` 字段，能正确反序列化 DeepSeek 流式响应。

**改动**：
- 删除 `DeepSeekStreamService`（87 行 HTTP 传输 + SSE 解析）
- `ChatService` 流式解析从手动 `JsonNode` 遍历换为 `ChatCompletionChunk`
- `ChatCompletionMessage` 类型替代 `delta.path("content").asText()` 写法
- 保留了自研的 `buildRequestBody()`（Map 构建）、`ToolRegistry`、工具编排逻辑

**启示**：**用框架的类型解析，保留自己的编排控制**——类型安全但不失灵活性。

---

## 12. v4-flash 默认思考模式

**发现**：`deepseek-v4-flash` **不传 `thinking: {type: "enabled"}` 也返回 `reasoning_content`**——模型默认开启思考。这意味着 `DeepSeekChatOptions` 没有 thinking 选项不是问题，不需要额外参数。

---

## 13. 并行 tool_calls 参数粘连（DSML 根因）

**症状**：模型并行调用多个工具时，参数粘在一起形成非法 JSON：
```
args={"keyword": "Redis持久化"}{"query": "Redis持久化策略 AOF RDB", "topK": 5}
```

**根因**：流式解析用单个 `StringBuilder` 累积所有 tool_call 的 arguments，不分 index。

**修复**（2026-06-26）：
- 用 `Map<Integer, String> tcNames` + `Map<Integer, StringBuilder> tcArgsMap` 按 tool_call index 独立累积
- `handleToolCallAndContinue` 接收 `List<ToolCall>`，多工具合并到一个 assistant 消息
- `buildMessagesWithTools` 构建含多 tool_calls 的 assistant 消息，逐一追加 tool 结果

**这也是 DSML 出现的根本原因**：工具执行失败 → 模型失去信心 → fallback 到 DSML。

---

## 教训

1. **非标准字段不信任任何 SDK** — 直接从 HTTP 层验证（reasoning_content 至今只有 DeepSeek 专用类型支持）
2. **用框架的类型，保留自己的控制** — `DeepSeekApi.ChatCompletionChunk` 做解析，`buildRequestBody()` 做请求构建
3. **先验证最底层再往上走** — 别猜，写测试
4. **模型能力差异藏得深** — DSML vs tool_calls，文档不一定写
5. **空字符串 != null** — 判空永远加 `isEmpty()`
6. **并行 tool_calls 参数别粘一起** — 按 index 独立累积
7. **DSML 不是 bug，是症状** — 工具执行失败 → 模型失信 → fallback；修好了工具循环，DSML 自然少
