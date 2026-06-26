<script setup>
import { computed } from 'vue'
import { state, USER } from '../stores/state'
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
      <span style="font-size:14px">💬</span><span class="text">{{ c.title || '新对话' }}</span>
      <span v-if="!state.batchMode" class="del" @click.stop="deleteConvo(c.id)" title="删除">✕</span>
    </div>
  </div>
  <div class="sidebar-footer">
    <div class="sidebar-profile">
      <img class="sidebar-avatar" :src="USER.avatar||''" alt="">
      <div class="sidebar-profile-info"><span class="sidebar-profile-name">{{ USER.nickname||'用户' }}</span><span v-if="USER.role==='ADMIN'" class="sidebar-profile-role">ADMIN</span></div>
      <div class="sidebar-profile-actions"><a href="settings.html" class="sidebar-action-btn" title="设置">⚙</a></div>
    </div>
    <a v-if="USER.role==='ADMIN'" href="admin.html" class="sidebar-admin-link">⚙ 管理后台</a>
    <div style="display:flex;gap:6px">
      <button class="sidebar-new" @click="newChat" style="flex:1">+ 新对话</button>
      <button class="sidebar-new" @click="state.batchMode=!state.batchMode;state.batchSelected={}" title="批量管理" style="flex:0;padding:0 12px">🗑</button>
    </div>
  </div>
</aside>
</template>
