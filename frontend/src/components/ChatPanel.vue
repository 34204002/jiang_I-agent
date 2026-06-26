<script setup>
import { ref, computed, nextTick } from 'vue'
import { state, AGENT_NAME, AGENT_AVATAR, USER } from '../stores/state'
import { loadConversations, newChat } from '../utils/chat'
import { marked } from 'marked'

const streamContent = ref('')
const streamThinking = ref('')
const thinkHdr = computed(() => state.toolRunning ? `🔧 调用: ${state.toolRunning}` : '🧠 思考中...')

function send() {
  const input = document.getElementById('msgInput')
  const text = input?.value.trim()
  if (!text || state.streaming) return
  if (input) input.value = ''
  state.messages = [...state.messages, { role: 'user', content: text }]
  state.streaming = true; state.toolRunning = null
  streamContent.value = ''; streamThinking.value = ''

  const url = `/api/chat/stream?message=${encodeURIComponent(text)}&token=${encodeURIComponent(localStorage.getItem('token')||'')}${state.conversationId ? '&conversationId='+state.conversationId : ''}${state.thinking ? '&thinking=true' : ''}`

  if (window._activeES) window._activeES.close()
  const es = new EventSource(url)
  window._activeES = es

  es.onmessage = e => {
    try {
      const evt = JSON.parse(e.data)
      if (evt.type === 'thinking') { streamThinking.value += evt.content; return }
      if (evt.type === 'content') { streamContent.value += evt.content; return }
      if (evt.type === 'tool_call') { state.toolRunning = evt.name; return }
    } catch (_) {}
  }

  es.onerror = () => {
    es.close(); window._activeES = null; state.streaming = false; state.toolRunning = null
    if (streamContent.value) {
      const save = streamThinking.value ? `<thinking>${streamThinking.value}</thinking>\n${streamContent.value}` : streamContent.value
      state.messages = [...state.messages, { role: 'assistant', content: save }]
    }
    streamContent.value = ''; streamThinking.value = ''
    loadConversations()
    if (!state.conversationId) setTimeout(() => { if (state.convos.length && !state.conversationId) state.conversationId = String(state.convos[0].id) }, 600)
  }
}

function sendHint(t) { const i = document.getElementById('msgInput'); if (i) { i.value = t; send() } }
function avatar(isUser) {
  const url = isUser ? (USER.avatar||'') : AGENT_AVATAR
  if (url) return `<img class="msg-avatar-img" src="${url.replace(/"/g,'&quot;')}" alt="">`
  return `<div class="msg-avatar-fallback">${isUser ? '👤' : '🤖'}</div>`
}
</script>

<template>
<div class="chat-body" id="chatBody" style="flex:1;overflow-y:auto">
  <div v-if="!state.conversationId && !state.messages.length" class="welcome">
    <div class="welcome-emoji"><svg width="72" height="72" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width=".8"><path d="M12 2a5 5 0 0 1 5 5c0 2.5-1.2 4-2.5 5.5h-5c-1.3-1.5-2.5-3-2.5-5.5a5 5 0 0 1 5-5z"/><path d="M7 16v5a3 3 0 0 0 3 3h4a3 3 0 0 0 3-3v-5"/><line x1="8" y1="21" x2="16" y2="21"/></svg></div>
    <h2>Jiang I-Agent</h2><p>你的个人 AI 知识库助手。</p>
    <div class="welcome-cards">
      <button class="welcome-card" @click="sendHint('帮我记一下面试题')">✔ 新建待办</button>
      <button class="welcome-card" @click="sendHint('用Java写一个快速排序')">💻 代码问答</button>
      <button class="welcome-card" @click="sendHint('解释 RAG 检索增强生成的原理')">📚 知识检索</button>
    </div>
  </div>
  <div v-for="(m, i) in state.messages" :key="i" :class="['msg', m.role]">
    <div v-html="avatar(m.role==='user')" class="msg-avatar"></div>
    <div class="msg-body"><div class="msg-label">{{ m.role==='user' ? 'You' : AGENT_NAME }}</div><div class="msg-bubble" v-html="marked.parse(m.content||'')"></div></div>
  </div>
  <div v-if="state.streaming" class="msg assistant">
    <div v-html="avatar(false)" class="msg-avatar"></div>
    <div class="msg-body"><div class="msg-label">{{ AGENT_NAME }}</div>
      <div v-if="streamThinking" class="thinking-block">
        <div class="thinking-header">{{ thinkHdr }}</div>
        <div class="thinking-body" v-html="marked.parse(streamThinking)"></div>
      </div>
      <div class="msg-bubble" v-html="marked.parse(streamContent)"></div>
    </div>
  </div>
</div>
<div id="inputArea" class="input-area">
  <button :class="['btn-think', { active: state.thinking }]" @click="state.thinking=!state.thinking">🧠 思考</button>
  <div class="input-row">
    <input placeholder="输入消息…" id="msgInput" @keydown.enter="send" :disabled="state.streaming">
    <button class="send-btn" @click="send" :disabled="state.streaming"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 2L11 13"/><path d="M22 2l-7 20-4-9-9-4 20-7z"/></svg></button>
  </div>
  <div class="input-footnote">{{ state.streaming ? '··· AI 正在生成...' : (state.thinking ? '🧠 思考模式' : 'Jiang I-Agent') }}</div>
</div>
</template>
