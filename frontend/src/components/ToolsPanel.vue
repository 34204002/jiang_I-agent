<script setup>
import { ref, onMounted } from 'vue'
import { state } from '../stores/state'
import { api } from '../utils/api'

const todos = ref([])
const newTitle = ref('')

async function load() {
  const json = await api.get(`/api/todos?status=${state.todoFilter}&page=1&size=50`)
  if (json.code===200 && json.data) todos.value = json.data.records || []
}
async function add() { if (!newTitle.value.trim()) return; await api.post('/api/todos', { title:newTitle.value.trim() }); newTitle.value=''; load() }
async function toggle(t) { await api.post(`/api/todos/${t.id}/toggle`, {}); load() }
async function del(t) { await api.del(`/api/todos/${t.id}`); load() }
onMounted(load)
</script>

<template>
<div class="kbase-panel">
  <div class="kbase-toolbar">
    <input class="kbase-search-input" v-model="newTitle" @keydown.enter="add" placeholder="新待办…">
    <button class="kbase-search-btn" style="background:var(--accent);color:#fff" @click="add">添加</button>
    <select v-model="state.todoFilter" class="kbase-search-input" style="max-width:120px;margin-left:auto" @change="load">
      <option value="pending">未完成</option><option value="done">已完成</option>
    </select>
  </div>
  <div v-if="todos.length" class="doc-list" style="flex:1;overflow-y:auto">
    <div v-for="t in todos" :key="t.id" class="graph-concept-card" style="display:flex;align-items:center;gap:10px">
      <input type="checkbox" :checked="t.isDone" @change="toggle(t)" style="accent-color:var(--accent);width:18px;height:18px">
      <span style="flex:1;font-size:14px" :style="{textDecoration:t.isDone?'line-through':'none',color:t.isDone?'var(--text-tertiary)':'var(--text-primary)'}">{{ t.title }}</span>
      <span style="font-size:12px;color:var(--text-tertiary)" v-if="t.dueDate">{{ t.dueDate }}</span>
      <button class="kbase-search-btn" style="font-size:11px;padding:2px 8px" @click="del(t)">删</button>
    </div>
  </div>
  <div v-else style="padding:50px;text-align:center;color:var(--text-tertiary)">暂无待办</div>
</div>
</template>
