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

## 2. DSML 格式函数调用 + 解析方案演化

**症状**：接入 Tool 后，DeepSeek V3.2 不在 `delta.tool_calls` 返回函数调用，而是在 `delta.content` 里输出自创标签：

```
<DSML｜function_calls>
<DSML｜invoke name="get_current_time">
</DSML｜invoke>
</DSML｜function_calls>
```

**根因**：DeepSeek V3.2 不支持 OpenAI function calling，用 DSML 替代。

**三次迭代**：

| 尝试 | 做法 | 失败原因 |
|------|------|---------|
| 逐 chunk 检测 | 每个 SSE chunk 搜 `<DSML|` | 标签跨 chunk 分割，`indexOf` 找不到 |
| 流结束扫完整 buffer | 全部缓冲后一次性扫描 | DSML 在流式过程已显示到前端；且模型先发空 `delta.tool_calls`（`name=""`），`tcName[0]=""` 非 null 绕过了 DSML 检测 |
| 微批量缓冲 + 前缀残留 + 空名拦截 | 见下方 | ✅ |

**最终方案**：
- **前缀残留缓冲**：每片末尾检查 `<`、`<D`、`<DS` 等标签前缀，剥下来留到下一片拼接
- **微批量 flush**：攒到 200 字符或句子边界再扫，标签完整
- **空名拦截**：`delta.tool_calls` 只认非空 name；空名不拦截 content 流
- **IN_DSML 截留**：状态标记，块内内容不 emit 到前端
- **DsmlParser 提取**：最终独立为 `com.jiang.tool.DsmlParser`

**细节坑**：
- 全角竖线 `｜`（U+FF5C）与半角 `|`（U+007C）肉眼难辨
- 正则用 `[^<]` 等宽松匹配比精确字符可靠
- 判空：`"" != null`，必须用 `== null || isEmpty()`

---

## 3. 自建 Tool 体系

绕过 Spring AI 后，`@Tool` 自动发现/执行体系无法使用，自建：

| 组件 | 职责 |
|------|------|
| `@Tool` 注解 | name + description + JSON Schema |
| `ToolRegistry` | `ApplicationReadyEvent` 时扫描、运行时反射执行 |
| `ToolContext` | ThreadLocal 传递 userId/convoId |
| `DsmlParser` | DSML 格式解析 |
| `ChatService` 双通道 | `delta.tool_calls`（标准）+ DSML（DeepSeek） |

注册 12 个工具：`create_todo`、`list_todos`、`complete_todo`、`delete_todo`、`get_current_time`、`search_knowledge`、`read_web_page`、`create_reminder`、`list_reminders`、`cancel_reminder`、`search_conversation`、`export_conversation`。

流程：模型输出 tool_call → 解析执行 → 结果注入 messages → 二次 LLM → 最终回答，最多 5 轮。

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

## 教训

1. **非标准字段不信任任何 SDK** — 直接从 HTTP 层验证
2. **框架越厚排查越难** — HttpClient 直连虽然多写代码但透明可控
3. **先验证最底层再往上走** — 别猜，写测试
4. **模型能力差异藏得深** — DSML vs tool_calls，文档不一定写
5. **空字符串 != null** — 判空永远加 `isEmpty()`
6. **全角字符睁眼瞎** — `｜` vs `|` 肉眼难辨，宽松匹配保平安
7. **流式标签不走逐 chunk** — 前缀缓冲 + 批量 flush + 状态标记是正解
