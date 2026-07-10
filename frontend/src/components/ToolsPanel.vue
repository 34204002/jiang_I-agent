<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {state} from '../stores/state'
import {api} from '../utils/api'
import CloseIcon from './icons/CloseIcon.vue'
import type {PageResult, TodoItem, ToolInfo} from '../types'

const todos = ref<TodoItem[]>([])
const tools = ref<ToolInfo[]>([])
const newTitle = ref('')

async function loadTodos() {
  const json = await api.get<PageResult<TodoItem>>(`/api/todos?done=${state.todoFilter === 'done'}&page=1&size=50`)
  if (json.code === 200 && json.data) todos.value = json.data.records || []
}

async function loadTools() {
  const json = await api.get<ToolInfo[] | { tools: ToolInfo[] }>('/api/tools')
  if (json.code === 200 && json.data) tools.value = Array.isArray(json.data) ? json.data : (json.data.tools || [])
}

async function add() {
  if (!newTitle.value.trim()) return;
  await api.post('/api/todos', {title: newTitle.value.trim()});
  newTitle.value = '';
  loadTodos()
}

async function toggle(t: TodoItem) {
  await api.put(`/api/todos/${t.id}/complete`, {});
  loadTodos()
}

async function del(t: TodoItem) {
  await api.del(`/api/todos/${t.id}`);
  loadTodos()
}

onMounted(() => {
  loadTodos();
  loadTools()
})
</script>

<template>
  <div class="kbase-panel">
    <!-- Available Tools -->
    <div class="tools-grid">
      <div v-for="t in tools" :key="t.name" :title="t.description" class="tool-chip">
        <span class="tool-chip-name">{{ t.name }}</span>
        <span class="tool-chip-desc">{{ t.description }}</span>
      </div>
      <div v-if="!tools.length" class="tool-empty">加载工具列表…</div>
    </div>

    <!-- Todo Section -->
    <div class="kbase-toolbar">
      <input v-model="newTitle" class="kbase-search-input" placeholder="新待办…" @keydown.enter="add">
      <button class="kbase-search-btn todo-add-btn" type="button" @click="add">添加
      </button>
      <select v-model="state.todoFilter" class="kbase-search-input todo-filter"
              @change="loadTodos">
        <option value="pending">未完成</option>
        <option value="done">已完成</option>
      </select>
    </div>

    <div v-if="todos.length" class="doc-list todo-list">
      <div v-for="t in todos" :key="t.id" class="graph-concept-card todo-card">
        <input :checked="t.isDone" class="todo-check" type="checkbox"
               @change="toggle(t)">
        <span
            :style="{textDecoration:t.isDone?'line-through':'none',color:t.isDone?'var(--text-tertiary)':'var(--text-primary)'}"
            style="flex:1;font-size:14px">{{
            t.title
          }}</span>
        <span v-if="t.dueDate" class="todo-due">{{ t.dueDate }}</span>
        <button class="todo-del-btn" title="删除" type="button" @click="del(t)">
          <CloseIcon :size="12"/>
        </button>
      </div>
    </div>
    <div v-else class="todo-empty">
      暂无待办，输入标题添加
    </div>
  </div>
</template>

<style scoped>
.todo-del-btn {
  font-size: 11px;
  cursor: pointer;
  border: none;
  color: var(--text-tertiary);
  background: none;
  padding: 2px;
  border-radius: 4px;
}

.todo-del-btn:hover {
  background: #FEE2E2;
  color: #EF4444
}

.tool-empty {
  padding: 6px 14px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.todo-add-btn {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
}

.todo-filter {
  max-width: 120px;
  margin-left: auto;
}

.todo-list {
  flex: 1;
  overflow-y: auto;
}

.todo-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--bg-surface, #fff);
  border-radius: 10px;
  margin-bottom: 6px;
  border: 1px solid var(--border);
}

.todo-check {
  accent-color: var(--accent);
  width: 18px;
  height: 18px;
}

.todo-due {
  font-size: 12px;
  color: var(--text-tertiary);
}

.todo-empty {
  padding: 40px 0;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}
</style>
