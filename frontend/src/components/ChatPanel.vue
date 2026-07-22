<script lang="ts" setup>
import {computed, reactive, ref, useTemplateRef, watch} from 'vue'
import {activeStreamUrl, agent, state, USER} from '../stores/state'
import {loadConversations} from '../stores/chat'
import {token} from '../utils/storage'
import {marked} from 'marked'
import DOMPurify from 'dompurify'
import ChevronIcon from './icons/ChevronIcon.vue'

interface Attachment {
  filename: string
  fileType: string
  content: string
}

interface MsgAttachment {
  filename: string
  fileType: string
}

const msgInput = useTemplateRef<HTMLInputElement>('msgInput')
const streamContent = ref('')
const streamThinking = ref('')
const streamThinkCollapsed = ref(false)
const thinkingCollapsed = reactive<Record<number, boolean>>({})
const thinkHdr = computed(() => state.toolRunning ? `调用: ${state.toolRunning}` : '思考中...')
const attachments = ref<Attachment[]>([])
const dragOver = ref(false)

let es: EventSource | null = null
let streamAbort: AbortController | null = null
let hasContent = false

function mdSafe(text: string): string {
  return DOMPurify.sanitize(marked.parse(text || '') as string)
}

function closeStream() {
  if (es) { es.close(); es = null }
  if (streamAbort) { streamAbort.abort(); streamAbort = null }
  activeStreamUrl.value = ''
}

function finalizeMessage() {
  closeStream()
  state.streaming = false
  state.toolRunning = null
  if (streamContent.value) {
    const lastIdx = state.messages.length
    state.messages = [...state.messages, {
      role: 'assistant',
      thinking: streamThinking.value || '',
      content: streamContent.value
    }]
    if (streamThinking.value) thinkingCollapsed[lastIdx] = true
  }
  streamContent.value = ''
  streamThinking.value = ''
  streamThinkCollapsed.value = false
  loadConversations()
  if (!state.conversationId) setTimeout(() => {
    if (state.convos.length && !state.conversationId) state.conversationId = String(state.convos[0].id)
  }, 600)
}

watch(activeStreamUrl, (url) => {
  if (!url && es) closeStream()
})

// ====== 文件类型图标（SVG） ======

const FILE_TYPE_COLORS: Record<string, string> = {
  pdf: '#EF4444',   // red
  docx: '#3B82F6',  // blue
  md: '#8B5CF6',    // lavender
  txt: '#6B7280',   // gray
}

function fileTypeLabel(ext: string): string {
  return ext.toUpperCase()
}

// ====== 文件上传 ======

async function uploadFile(file: File): Promise<Attachment | null> {
  const form = new FormData()
  form.append('file', file)
  try {
    const resp = await fetch('/api/chat/upload', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token.value}` },
      body: form
    })
    const json = await resp.json()
    if (json.code !== 200) {
      console.warn('文件上传失败:', json.message)
      return null
    }
    return json.data as Attachment
  } catch (e) {
    console.warn('文件上传异常:', e)
    return null
  }
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  dragOver.value = true
}
function onDragLeave(e: DragEvent) {
  e.preventDefault()
  dragOver.value = false
}
async function onDrop(e: DragEvent) {
  e.preventDefault()
  dragOver.value = false
  const files = e.dataTransfer?.files
  if (!files) return
  for (let i = 0; i < files.length; i++) {
    const f = files[i]
    const ext = f.name.split('.').pop()?.toLowerCase() || ''
    if (!['pdf','md','txt','docx'].includes(ext)) continue
    if (f.size > 20 * 1024 * 1024) continue
    if (attachments.value.some(a => a.filename === f.name)) continue
    const att = await uploadFile(f)
    if (att) attachments.value = [...attachments.value, att]
  }
}

function removeAttachment(idx: number) {
  attachments.value = attachments.value.filter((_, i) => i !== idx)
}

// ====== 发送消息 ======

function send() {
  const text = msgInput.value?.value?.trim()
  if (!text || state.streaming) return
  if (msgInput.value) msgInput.value.value = ''

  // 无附件 → EventSource GET（浏览器原生 SSE 解析）
  if (attachments.value.length === 0) {
    doSendSimple(text)
    return
  }

  // 有附件 → 文件内容太大，GET URL 放不下，走 POST fetch
  doSendWithFiles(text)
}

/** 纯文本 — EventSource GET */
function doSendSimple(text: string) {
  state.messages = [...state.messages, {role: 'user', content: text}]
  beginStream()
  const url = `/api/chat/stream?message=${encodeURIComponent(text)}&token=${encodeURIComponent(token.value)}${state.conversationId ? '&conversationId=' + state.conversationId : ''}${state.thinking ? '&thinking=true' : ''}`
  closeStream()
  activeStreamUrl.value = url

  es = new EventSource(url)
  es.onmessage = handleSSEEvent
  es.onerror = () => {
    if (!hasContent) return
    es?.close(); es = null
    finalizeMessage()
  }
}

/** 带附件 — POST fetch（文件内容在 JSON body） */
function doSendWithFiles(text: string) {
  const fileMetas: MsgAttachment[] = attachments.value.map(a => ({
    filename: a.filename, fileType: a.fileType
  }))
  state.messages = [...state.messages, {
    role: 'user' as const, content: text,
    ...(fileMetas.length > 0 ? { attachments: fileMetas } as any : {})
  }]
  beginStream()

  const body: Record<string, unknown> = {
    message: text,
    ...(state.conversationId ? { conversationId: state.conversationId } : {}),
    attachments: attachments.value
  }
  closeStream()
  attachments.value = []
  streamAbort = new AbortController()

  fetchSSE(`/api/chat/stream?thinking=${state.thinking}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token.value}` },
    body: JSON.stringify(body),
    signal: streamAbort.signal
  })
}

/** 统一流式开始状态 */
function beginStream() {
  state.streaming = true
  state.toolRunning = null
  streamContent.value = ''
  streamThinking.value = ''
  streamThinkCollapsed.value = false
  hasContent = false
}

/** EventSource 事件处理 */
function handleSSEEvent(e: MessageEvent) {
  try {
    const evt = JSON.parse(e.data)
    if (evt.type === 'thinking') { streamThinking.value += evt.content; return }
    if (evt.type === 'content') { streamContent.value += evt.content; hasContent = true; return }
    if (evt.type === 'tool_call') { state.toolRunning = evt.name; streamContent.value = ''; return }
  } catch { /* ignore */ }
}

/** fetch 手动解析 SSE — 按 \n\n 分隔事件 */
async function fetchSSE(url: string, opts: RequestInit) {
  try {
    const resp = await fetch(url, opts)
    if (!resp.ok) {
      streamContent.value = `（请求失败: ${resp.status}）`
      finalizeMessage()
      return
    }
    const reader = resp.body?.getReader()
    if (!reader) { finalizeMessage(); return }

    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })

      // SSE 事件以 \n\n 结尾
      let idx: number
      while ((idx = buf.indexOf('\n\n')) !== -1) {
        const raw = buf.slice(0, idx)
        buf = buf.slice(idx + 2)

        // 取 data: 行
        const dataLine = raw.split('\n').find(l => l.startsWith('data: '))
        if (!dataLine) continue
        try {
          const evt = JSON.parse(dataLine.slice(6))
          if (evt.type === 'thinking') { streamThinking.value += evt.content }
          else if (evt.type === 'content') { streamContent.value += evt.content; hasContent = true }
          else if (evt.type === 'tool_call') { state.toolRunning = evt.name; streamContent.value = '' }
        } catch { /* ignore */ }
      }
    }
  } catch (e: unknown) {
    if (e instanceof DOMException && e.name === 'AbortError') return
    if (!hasContent) { streamContent.value = '（网络异常，请重试）' }
  }
  finalizeMessage()
}

function sendHint(t: string) {
  if (msgInput.value) {
    msgInput.value.value = t
    send()
  }
}

function avatar(isUser: boolean): string {
  const url = isUser ? (USER.avatar || '') : agent.avatar
  if (url) return `<img class="msg-avatar-img" src="${url.replace(/"/g, '&quot;')}" alt="">`
  return `<div class="msg-avatar-fallback"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">${isUser ? '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>' : '<path d="M12 2a5 5 0 0 1 5 5c0 2.5-1.2 4-2.5 5.5h-5c-1.3-1.5-2.5-3-2.5-5.5a5 5 0 0 1 5-5z"/><path d="M7 16v5a3 3 0 0 0 3 3h4a3 3 0 0 0 3-3v-5"/>'}</svg></div>`
}
</script>

<template>
  <div :class="['chat-shell', { 'drag-over': dragOver }]"
       @dragover="onDragOver" @dragleave="onDragLeave" @drop="onDrop">
    <div class="drag-overlay" v-if="dragOver">
      <div class="drag-overlay-text">释放文件以上传</div>
    </div>
    <div id="chatBody" class="chat-body">
      <div v-if="!state.conversationId && !state.messages.length" class="welcome">
        <div class="welcome-emoji">
          <svg fill="none" height="64" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" viewBox="0 0 24 24" width="64">
            <path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.9 0 1.8-.1 2.6-.4l4.1 1.3-1.1-3.8C19.5 17.3 22 14.9 22 12c0-5.5-4.5-10-10-10z"/>
            <circle cx="8" cy="12" r="1" fill="currentColor" stroke="none"/>
            <circle cx="12" cy="12" r="1" fill="currentColor" stroke="none"/>
            <circle cx="16" cy="12" r="1" fill="currentColor" stroke="none"/>
          </svg>
        </div>
        <h2>Jiang I-Agent</h2>
        <p>你的个人 AI 知识库助手。</p>
        <div class="welcome-cards">
          <button class="welcome-card" type="button" @click="sendHint('帮我记一下面试题')">新建待办</button>
          <button class="welcome-card" type="button" @click="sendHint('用Java写一个快速排序')">代码问答</button>
          <button class="welcome-card" type="button" @click="sendHint('解释 RAG 检索增强生成的原理')">知识检索</button>
          <button class="welcome-card" type="button" @click="sendHint('HashMap的底层实现原理')">技术问答</button>
        </div>
      </div>
      <div v-for="(m, i) in state.messages" :key="i" :class="['msg', m.role]">
        <div class="msg-avatar" v-html="avatar(m.role==='user')"></div>
        <div class="msg-body">
          <div class="msg-label">{{ m.role === 'user' ? 'You' : agent.name }}</div>
          <template v-if="m.role==='assistant' && m.thinking">
            <div :class="{ collapsed: thinkingCollapsed[i] }" class="thinking-block">
              <div class="thinking-header" @click="thinkingCollapsed[i] = !thinkingCollapsed[i]">
                <ChevronIcon :class="['thinking-chevron', { open: !thinkingCollapsed[i] }]"/>
                <span>思考内容</span>
              </div>
              <div class="thinking-body" v-html="mdSafe(m.thinking||'')"></div>
            </div>
          </template>
          <!-- 文件附件卡片（用户消息内） -->
          <div v-if="m.role==='user' && (m as any).attachments?.length" class="msg-files">
            <div v-for="(fa: MsgAttachment, fi: number) in (m as any).attachments" :key="fi"
                 :class="['msg-file-card', `ext-${fa.fileType}`]">
              <!-- 文件图标 SVG -->
              <svg class="msg-file-icon" width="32" height="40" viewBox="0 0 32 40" fill="none">
                <path d="M3 1h18l9 9v28a2 2 0 01-2 2H3a2 2 0 01-2-2V3a2 2 0 012-2z"
                      :stroke="FILE_TYPE_COLORS[fa.fileType] || '#6B7280'" stroke-width="1.5" fill="white"/>
                <path d="M21 1v8a1 1 0 001 1h8" :fill="FILE_TYPE_COLORS[fa.fileType] || '#6B7280'" :stroke="FILE_TYPE_COLORS[fa.fileType] || '#6B7280'" stroke-width="1"/>
                <text :x="fa.fileType.length > 3 ? 3.5 : 6" y="30" :fill="FILE_TYPE_COLORS[fa.fileType] || '#6B7280'"
                      font-size="7" font-weight="700" font-family="Inter, sans-serif">{{ fileTypeLabel(fa.fileType) }}</text>
              </svg>
              <span class="msg-file-name">{{ fa.filename }}</span>
            </div>
          </div>
          <div class="msg-bubble" v-html="mdSafe(m.content||'')"></div>
        </div>
      </div>
      <div v-if="state.streaming" class="msg assistant">
        <div class="msg-avatar" v-html="avatar(false)"></div>
        <div class="msg-body">
          <div class="msg-label">{{ agent.name }}</div>
          <div v-if="streamThinking" :class="{ collapsed: streamThinkCollapsed }" class="thinking-block">
            <div class="thinking-header" @click="streamThinkCollapsed = !streamThinkCollapsed">
              <ChevronIcon :class="['thinking-chevron', { open: !streamThinkCollapsed }]"/>
              <span>{{ thinkHdr }}</span>
            </div>
            <div class="thinking-body" v-html="mdSafe(streamThinking)"></div>
          </div>
          <div :class="{ cursor: state.streaming && streamContent }" class="msg-bubble"
               v-html="mdSafe(streamContent)"></div>
        </div>
      </div>
    </div>
    <div id="inputArea" class="input-area">
      <!-- 文件预览 chips -->
      <div v-if="attachments.length" class="attach-chips">
        <span v-for="(a, idx) in attachments" :key="idx" :class="['attach-chip', `ext-${a.fileType}`]">
          <svg class="attach-chip-svg" width="16" height="20" viewBox="0 0 16 20" fill="none">
            <rect x="1" y="1" width="14" height="18" rx="1.5" :stroke="FILE_TYPE_COLORS[a.fileType] || '#6B7280'" stroke-width="1.2" fill="white"/>
            <text :x="a.fileType.length > 3 ? 1.5 : 3" y="15" :fill="FILE_TYPE_COLORS[a.fileType] || '#6B7280'"
                  font-size="5" font-weight="700" font-family="Inter, sans-serif">{{ fileTypeLabel(a.fileType) }}</text>
          </svg>
          <span class="attach-chip-name">{{ a.filename }}</span>
          <button class="attach-chip-remove" type="button" @click="removeAttachment(idx)" title="移除">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </button>
        </span>
      </div>
      <button :class="['btn-think', { active: state.thinking }]" type="button" @click="state.thinking=!state.thinking">
        思考
      </button>
      <div class="input-row">
        <input ref="msgInput" :disabled="state.streaming" placeholder="输入消息… 或拖拽文件到此处上传" @keydown.enter="send">
        <button :disabled="state.streaming" class="btn-send" type="button" @click="send">
          <svg fill="none" height="18" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="18">
            <path d="M22 2L11 13"/>
            <path d="M22 2l-7 20-4-9-9-4 20-7z"/>
          </svg>
        </button>
      </div>
      <div class="input-footnote">
        {{ state.streaming ? '··· AI 正在生成...' : (state.thinking ? '思考模式' : 'Jiang I-Agent') }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-shell { flex: 1; display: flex; flex-direction: column; min-height: 0; position: relative }
.chat-body { flex: 1; overflow-y: auto }

/* ====== 拖拽覆盖层 ====== */
.chat-shell.drag-over { outline: 2px dashed var(--accent); outline-offset: -4px }
.drag-overlay {
  position: absolute; inset: 0; z-index: 100;
  display: flex; align-items: center; justify-content: center;
  background: rgba(244,114,182,.08); pointer-events: none; border-radius: var(--radius)
}
.drag-overlay-text {
  font-size: 1.25rem; font-weight: 600; color: var(--accent);
  background: var(--bg-surface); padding: 16px 32px; border-radius: var(--radius-lg);
  box-shadow: var(--shadow)
}

/* ====== 消息内文件附件卡片 ====== */
.msg-files {
  display: flex; flex-wrap: wrap; gap: 6px;
  margin-bottom: 6px
}
.msg-file-card {
  display: inline-flex; align-items: center; gap: 8px;
  background: var(--bg-surface);
  border: 1px solid var(--border, #E2E8F0);
  border-radius: var(--radius-sm);
  padding: 6px 10px 6px 6px;
  max-width: 240px;
  transition: border-color .15s
}
.msg-file-card:hover { border-color: var(--accent) }
.msg-file-icon { flex-shrink: 0 }
.msg-file-name {
  font-size: .8rem; font-weight: 500; color: var(--text-primary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap
}

/* ====== 输入区文件 chips ====== */
.attach-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px }
.attach-chip {
  display: inline-flex; align-items: center; gap: 5px;
  background: var(--bg-surface);
  border: 1px solid var(--border, #E2E8F0);
  border-radius: var(--radius);
  padding: 4px 8px 4px 6px;
  font-size: .8rem; white-space: nowrap;
  transition: border-color .15s, box-shadow .15s
}
.attach-chip:hover { border-color: var(--accent); box-shadow: 0 1px 4px rgba(0,0,0,.06) }
.attach-chip-svg { flex-shrink: 0 }
.attach-chip-name {
  max-width: 140px; overflow: hidden; text-overflow: ellipsis;
  color: var(--text-primary); font-weight: 500
}
.attach-chip-remove {
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px; border-radius: 50%;
  background: none; border: none; cursor: pointer;
  color: var(--text-tertiary); transition: all .15s
}
.attach-chip-remove:hover {
  background: var(--color-error); color: #fff
}
</style>
