<script lang="ts" setup>
import {computed, reactive, ref} from 'vue'
import {agent, state, USER} from '../stores/state'
import {loadConversations} from '../utils/chat'
import {marked} from 'marked'
import DOMPurify from 'dompurify'

const msgInput = ref<HTMLInputElement | null>(null)
const streamContent = ref('')
const streamThinking = ref('')
const streamThinkCollapsed = ref(false)
const thinkingCollapsed = reactive<Record<number, boolean>>({})
const thinkHdr = computed(() => state.toolRunning ? `调用: ${state.toolRunning}` : '思考中...')

function mdSafe(text: string): string {
  return DOMPurify.sanitize(marked.parse(text || '') as string)
}

function send() {
  const text = msgInput.value?.value?.trim()
  if (!text || state.streaming) return
  if (msgInput.value) msgInput.value.value = ''
  state.messages = [...state.messages, {role: 'user', content: text}]
  state.streaming = true;
  state.toolRunning = null
  streamContent.value = '';
  streamThinking.value = '';
  streamThinkCollapsed.value = false

  const url = `/api/chat/stream?message=${encodeURIComponent(text)}&token=${encodeURIComponent(localStorage.getItem('token') || '')}${state.conversationId ? '&conversationId=' + state.conversationId : ''}${state.thinking ? '&thinking=true' : ''}`

  if (window._activeES) window._activeES.close()
  const es = new EventSource(url)
  window._activeES = es
  let hasContent = false

  es.onmessage = (e: MessageEvent) => {
    try {
      const evt = JSON.parse(e.data)
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
    } catch (_) {
    }
  }

  es.onerror = () => {
    if (!hasContent) return  // 网络闪断，让浏览器自动重连
    es.close();
    window._activeES = null;
    state.streaming = false;
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
    streamContent.value = '';
    streamThinking.value = '';
    streamThinkCollapsed.value = false
    loadConversations()
    if (!state.conversationId) setTimeout(() => {
      if (state.convos.length && !state.conversationId) state.conversationId = String(state.convos[0].id)
    }, 600)
  }
}

function sendHint(t: string) {
  if (msgInput.value) {
    msgInput.value.value = t;
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
  <div style="flex:1;display:flex;flex-direction:column;min-height:0">
    <div id="chatBody" class="chat-body" style="flex:1;overflow-y:auto">
      <div v-if="!state.conversationId && !state.messages.length" class="welcome">
        <div class="welcome-emoji">
          <svg fill="none" height="72" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24" width="72">
            <path d="M12 2a5 5 0 0 1 5 5c0 2.5-1.2 4-2.5 5.5h-5c-1.3-1.5-2.5-3-2.5-5.5a5 5 0 0 1 5-5z"/>
            <path d="M7 16v5a3 3 0 0 0 3 3h4a3 3 0 0 0 3-3v-5"/>
            <line x1="8" x2="16" y1="21" y2="21"/>
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
                <svg :class="{ open: !thinkingCollapsed[i] }" class="thinking-chevron" fill="none" height="12"
                     stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
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
              <svg :class="{ open: !streamThinkCollapsed }" class="thinking-chevron" fill="none" height="12"
                   stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                <polyline points="9 18 15 12 9 6"/>
              </svg>
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
