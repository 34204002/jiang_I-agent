<script lang="ts" setup>
import {computed, reactive, ref, useTemplateRef, watch} from 'vue'
import {useEventSource} from '@vueuse/core'
import {activeStreamUrl, agent, state, USER} from '../stores/state'
import {loadConversations} from '../stores/chat'
import {token} from '../utils/storage'
import {marked} from 'marked'
import DOMPurify from 'dompurify'
import ChevronIcon from './icons/ChevronIcon.vue'

const msgInput = useTemplateRef<HTMLInputElement>('msgInput')
const streamContent = ref('')
const streamThinking = ref('')
const streamThinkCollapsed = ref(false)
const thinkingCollapsed = reactive<Record<number, boolean>>({})
const thinkHdr = computed(() => state.toolRunning ? `调用: ${state.toolRunning}` : '思考中...')

const {data, status, close: closeES} = useEventSource(activeStreamUrl, [], {autoReconnect: false})
let hasContent = false

function mdSafe(text: string): string {
  return DOMPurify.sanitize(marked.parse(text || '') as string)
}

function finalizeMessage() {
  activeStreamUrl.value = ''
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

// 监听 SSE 数据到达
watch(data, (raw) => {
  if (!raw) return
  try {
    const evt = JSON.parse(raw)
    if (evt.type === 'thinking') {
      streamThinking.value += evt.content;
      return
    }
    if (evt.type === 'content') {
      streamContent.value += evt.content;
      hasContent = true;
      return
    }
    if (evt.type === 'tool_call') {
      state.toolRunning = evt.name;
      streamContent.value = '';
      return
    }
  } catch { /* ignore malformed JSON */
  }
})

// 监听连接状态：CONNECTING 表示浏览器在尝试重连，判断是闪断还是流结束
watch(status, (s) => {
  if (s !== 'CONNECTING') return
  if (!hasContent) return  // 网络闪断，让浏览器自动重连
  closeES();               // 已有内容 → 流正常结束，阻止重连
  finalizeMessage()
})

function send() {
  const text = msgInput.value?.value?.trim()
  if (!text || state.streaming) return
  if (msgInput.value) msgInput.value.value = ''
  state.messages = [...state.messages, {role: 'user', content: text}]
  state.streaming = true
  state.toolRunning = null
  streamContent.value = ''
  streamThinking.value = ''
  streamThinkCollapsed.value = false
  hasContent = false

  const url = `/api/chat/stream?message=${encodeURIComponent(text)}&token=${encodeURIComponent(token.value)}${state.conversationId ? '&conversationId=' + state.conversationId : ''}${state.thinking ? '&thinking=true' : ''}`
  activeStreamUrl.value = url
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
  <div class="chat-shell">
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
      <button :class="['btn-think', { active: state.thinking }]" type="button" @click="state.thinking=!state.thinking">
        思考
      </button>
      <div class="input-row">
        <input ref="msgInput" :disabled="state.streaming" placeholder="输入消息…" @keydown.enter="send">
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
.chat-shell {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0
}

.chat-body {
  flex: 1;
  overflow-y: auto
}
</style>
