<script setup>
import { computed } from 'vue'
import { state, USER, logout } from '../stores/state'
import { api } from '../utils/api'
import { newChat, loadConversations, selectConvo } from '../utils/chat'

const selCount = computed(() => Object.keys(state.batchSelected).length)
const allSelected = computed(() => state.convos.length > 0 && selCount.value === state.convos.length)

function toggleSel(id) { state.batchSelected[id] = !state.batchSelected[id] ? true : delete state.batchSelected[id] }
function toggleAll() { state.batchSelected = allSelected.value ? {} : Object.fromEntries(state.convos.map(c => [c.id, true])) }

async function deleteConvo(id) {
  if (!confirm('确定删除？')) return
  await api.del(`/api/conversations/${id}`)
  loadConversations()
  if (state.conversationId === String(id)) newChat()
}

async function batchDelete() {
  const ids = Object.keys(state.batchSelected).map(Number)
  if (!ids.length || !confirm(`确定删除选中的 ${ids.length} 个会话？`)) return
  await api.post('/api/conversations/batch-delete', { ids })
  state.batchMode = false; state.batchSelected = {}; loadConversations()
  if (ids.includes(Number(state.conversationId))) newChat()
}
</script>

<template>
<aside class="sidebar">
  <div class="sidebar-brand">
    <div class="icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg></div>
    <div><div class="name">Jiang I-Agent</div><div class="ver">DeepSeek v4 · 在线</div></div>
  </div>
  <div class="sidebar-list">
    <div v-if="!state.convos.length" class="sidebar-empty">暂无对话</div>
    <div v-if="state.batchMode && state.convos.length" class="batch-bar">
      <label class="batch-check-all"><input type="checkbox" :checked="allSelected" @change="toggleAll"> 全选</label>
      <button class="batch-del-btn" :disabled="selCount===0" @click="batchDelete">删除({{ selCount }})</button>
      <button class="batch-cancel-btn" @click="state.batchMode=false;state.batchSelected={}">取消</button>
    </div>
    <div v-for="c in state.convos" :key="c.id"
      :class="['sidebar-convo', { active: String(c.id)===state.conversationId }]"
      @click="state.batchMode ? toggleSel(c.id) : selectConvo(c.id)"
      :title="c.title||'新对话'">
      <input v-if="state.batchMode" type="checkbox" :checked="!!state.batchSelected[c.id]" class="batch-cb" @click.stop="toggleSel(c.id)">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="vertical-align:middle"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg> <span class="text">{{ c.title || '新对话' }}</span>
      <span v-if="!state.batchMode" class="del" @click.stop="deleteConvo(c.id)" title="删除"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></span>
    </div>
  </div>
  <div class="sidebar-footer">
    <div class="sidebar-profile">
      <img class="sidebar-avatar" :src="USER.avatar||''" alt="">
      <div class="sidebar-profile-info"><span class="sidebar-profile-name">{{ USER.nickname||'用户' }}</span><span v-if="USER.role==='ADMIN'" class="sidebar-profile-role">ADMIN</span></div>
      <div class="sidebar-profile-actions">
        <router-link to="/settings" class="sidebar-action-btn" title="设置"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg></router-link>
        <button type="button" class="sidebar-action-btn sidebar-logout-btn" title="退出登录" @click="logout"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg></button>
      </div>
    </div>
    <router-link v-if="USER.role==='ADMIN'" to="/admin" class="sidebar-admin-link"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg> 管理后台</router-link>
    <div class="sidebar-copyright">© 2026 Jiang · 仅供学习交流</div>
    <div style="display:flex;gap:6px">
      <button class="sidebar-new" @click="newChat" style="flex:1">+ 新对话</button>
      <button class="sidebar-new" @click="state.batchMode=!state.batchMode;state.batchSelected={}" title="批量管理" style="flex:0;padding:0 12px"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg></button>
    </div>
  </div>
</aside>
</template>
