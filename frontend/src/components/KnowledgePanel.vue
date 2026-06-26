<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../utils/api'
import { showToast } from '../utils/toast'

const docs = ref([])
const searchQuery = ref('')
const searchResult = ref(null)
const fileInput = ref(null)

async function loadDocs() {
  const json = await api.get('/api/knowledge/documents?page=1&size=50')
  if (json.code===200 && json.data) docs.value = json.data.records || []
}

async function uploadDoc(e) {
  const file = e.target.files[0]
  if (!file) return
  const form = new FormData(); form.append('file', file)
  const r = await fetch('/api/knowledge/documents', {
    method:'POST', headers:{'Authorization':'Bearer '+localStorage.getItem('token')}, body:form
  })
  const json = await r.json()
  if (json.code===200) { loadDocs(); showToast('上传成功','ok') } else showToast(json.message||'上传失败','error')
}

async function deleteDoc(id) {
  if (!confirm('确定删除该文档？')) return
  await api.del(`/api/knowledge/documents/${id}`)
  loadDocs()
}

async function search() {
  if (!searchQuery.value.trim()) return
  const json = await api.post('/api/knowledge/search', { query:searchQuery.value, topK:5 })
  if (json.code===200) searchResult.value = json.data
}

function downloadUrl(doc) {
  return doc.ossKey ? `/api/knowledge/documents/${doc.id}/download` : null
}

function formatSize(bytes) {
  if (!bytes) return ''
  return bytes>1048576 ? (bytes/1048576).toFixed(1)+'MB' : (bytes/1024).toFixed(0)+'KB'
}

onMounted(loadDocs)
</script>

<template>
<div class="kbase-panel" style="overflow-y:auto">
  <!-- Upload + Search toolbar -->
  <div class="kbase-toolbar">
    <input class="kbase-search-input" v-model="searchQuery" @keydown.enter="search" placeholder="搜索知识库…">
    <button type="button" class="kbase-search-btn" @click="search">搜索</button>
    <input type="file" ref="fileInput" style="display:none" accept=".pdf,.md,.txt,.docx" @change="uploadDoc">
    <button type="button" class="kbase-search-btn kbase-upload-accent" @click="fileInput.click()">上传文档</button>
  </div>

  <!-- Search result -->
  <div v-if="searchResult" style="margin-bottom:16px">
    <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
      <span style="font-size:13px;font-weight:700;color:var(--accent)">搜索结果</span>
      <button type="button" class="kbase-search-btn" style="font-size:11px;padding:2px 8px" @click="searchResult=null">清除</button>
    </div>
    <div style="font-size:14px;line-height:1.7;padding:16px;background:var(--bg-surface,#fff);border-radius:12px;margin-bottom:10px">{{ searchResult.answer }}</div>
    <div v-for="(s,i) in searchResult.sources" :key="i" style="padding:10px 14px;background:#fff;border-radius:10px;margin-bottom:6px;font-size:12px;color:var(--text-secondary)">
      <span style="font-weight:700;color:var(--accent)"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align:middle;margin-right:3px"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>{{ s.filename }}</span>
      <span style="margin-left:8px;font-size:11px;color:var(--text-tertiary)">相关度 {{ Math.round(s.score*100) }}%</span>
      <div style="margin-top:4px;color:var(--text-secondary)">{{ s.content?.substring(0,200) }}{{ s.content?.length>200?'…':'' }}</div>
    </div>
  </div>

  <!-- Document list -->
  <div style="font-size:13px;font-weight:700;color:var(--text-secondary);margin-bottom:8px">文档列表 ({{ docs.length }})</div>
  <div v-if="docs.length" class="doc-list" style="flex:1;overflow-y:auto">
    <div v-for="d in docs" :key="d.id" class="graph-concept-card" style="display:flex;align-items:center;gap:10px;padding:12px 16px;background:#fff;border-radius:10px;margin-bottom:6px;border:1px solid var(--border)">
      <svg v-if="d.fileType==='pdf'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#EF4444" stroke-width="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
      <svg v-else-if="d.fileType==='md'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#3B82F6" stroke-width="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
      <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#8B5CF6" stroke-width="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
      <span style="flex:1;font-size:14px;font-weight:600">{{ d.filename }}</span>
      <span style="font-size:12px;color:var(--text-tertiary)">{{ formatSize(d.fileSize) }}</span>
      <span style="font-size:11px;padding:2px 8px;border-radius:20px;background:var(--accent-subtle);color:var(--accent);font-weight:600">{{ d.status===2?'已向量化':d.status===1?'已解析':'待处理' }}</span>
      <a v-if="d.status===2&&downloadUrl(d)" :href="downloadUrl(d)" style="font-size:11px;color:var(--accent);text-decoration:none;font-weight:600">下载</a>
      <button type="button" class="doc-card-del" @click="deleteDoc(d.id)" title="删除"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
    </div>
  </div>
  <div v-else style="padding:40px;text-align:center;color:var(--text-tertiary);font-size:14px">暂无文档，上传 PDF/Markdown/TXT/DOCX</div>
</div>
</template>

<style scoped>
.kbase-upload-accent { background: var(--accent); color: #fff }
</style>
