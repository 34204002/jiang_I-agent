<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {api} from '../utils/api'
import {showToast} from '../utils/toast'
import type {DocumentItem, PageResult, SearchResponse} from '../types'

const docs = ref<DocumentItem[]>([])
const searchQuery = ref('')
const searchResult = ref<SearchResponse | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

async function loadDocs() {
  const json = await api.get<PageResult<DocumentItem>>('/api/knowledge/documents?page=1&size=50')
  if (json.code === 200 && json.data) docs.value = json.data.records || []
}

async function uploadDoc(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  const form = new FormData();
  form.append('file', file)
  const r = await fetch('/api/knowledge/documents', {
    method: 'POST', headers: {'Authorization': 'Bearer ' + localStorage.getItem('token')}, body: form
  })
  const json = await r.json()
  if (json.code === 200) {
    loadDocs();
    showToast('上传成功', 'ok')
  } else showToast(json.message || '上传失败', 'error')
}

async function deleteDoc(id: number) {
  if (!confirm('确定删除该文档？')) return
  await api.del(`/api/knowledge/documents/${id}`)
  loadDocs()
}

async function search() {
  if (!searchQuery.value.trim()) return
  const json = await api.post<SearchResponse>('/api/knowledge/search', {query: searchQuery.value, topK: 5})
  if (json.code === 200) searchResult.value = json.data
}

function downloadUrl(doc: { id: number; ossKey?: string }): string | undefined {
  return doc.ossKey ? `/api/knowledge/documents/${doc.id}/download` : undefined
}

function formatSize(bytes: number): string {
  if (!bytes) return ''
  return bytes > 1048576 ? (bytes / 1048576).toFixed(1) + 'MB' : (bytes / 1024).toFixed(0) + 'KB'
}

onMounted(loadDocs)
</script>

<template>
  <div class="kbase-panel" style="overflow-y:auto">
    <!-- Upload + Search toolbar -->
    <div class="kbase-toolbar">
      <input v-model="searchQuery" class="kbase-search-input" placeholder="搜索知识库…" @keydown.enter="search">
      <button class="kbase-search-btn" type="button" @click="search">搜索</button>
      <input ref="fileInput" accept=".pdf,.md,.txt,.docx" style="display:none" type="file" @change="uploadDoc">
      <button class="kbase-search-btn kbase-upload-accent" type="button" @click="fileInput?.click()">上传文档</button>
    </div>

    <!-- Search result -->
    <div v-if="searchResult" style="margin-bottom:16px">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
        <span style="font-size:13px;font-weight:700;color:var(--accent)">搜索结果</span>
        <button class="kbase-search-btn" style="font-size:11px;padding:2px 8px" type="button"
                @click="searchResult=null">清除
        </button>
      </div>
      <div
          style="font-size:14px;line-height:1.7;padding:16px;background:var(--bg-surface,#fff);border-radius:12px;margin-bottom:10px">
        {{ searchResult.answer }}
      </div>
      <div v-for="(s,i) in searchResult.sources" :key="i"
           style="padding:10px 14px;background:#fff;border-radius:10px;margin-bottom:6px;font-size:12px;color:var(--text-secondary)">
        <span style="font-weight:700;color:var(--accent)"><svg fill="none" height="12" stroke="currentColor" stroke-width="2"
                                                               style="vertical-align:middle;margin-right:3px" viewBox="0 0 24 24"
                                                               width="12"><path
            d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>{{
            s.filename
          }}</span>
        <span style="margin-left:8px;font-size:11px;color:var(--text-tertiary)">相关度 {{
            Math.round(s.score * 100)
          }}%</span>
        <div style="margin-top:4px;color:var(--text-secondary)">{{
            s.content?.substring(0, 200)
          }}{{ s.content?.length > 200 ? '…' : '' }}
        </div>
      </div>
    </div>

    <!-- Document list -->
    <div style="font-size:13px;font-weight:700;color:var(--text-secondary);margin-bottom:8px">文档列表 ({{
        docs.length
      }})
    </div>
    <div v-if="docs.length" class="doc-list" style="flex:1;overflow-y:auto">
      <div v-for="d in docs" :key="d.id" class="graph-concept-card"
           style="display:flex;align-items:center;gap:10px;padding:12px 16px;background:#fff;border-radius:10px;margin-bottom:6px;border:1px solid var(--border)">
        <svg v-if="d.fileType==='pdf'" fill="none" height="18" stroke="#EF4444" stroke-width="1.5" viewBox="0 0 24 24"
             width="18">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" x2="8" y1="13" y2="13"/>
          <line x1="16" x2="8" y1="17" y2="17"/>
        </svg>
        <svg v-else-if="d.fileType==='md'" fill="none" height="18" stroke="#3B82F6" stroke-width="1.5" viewBox="0 0 24 24"
             width="18">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" x2="8" y1="13" y2="13"/>
          <line x1="16" x2="8" y1="17" y2="17"/>
        </svg>
        <svg v-else fill="none" height="18" stroke="#8B5CF6" stroke-width="1.5" viewBox="0 0 24 24" width="18">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" x2="8" y1="13" y2="13"/>
          <line x1="16" x2="8" y1="17" y2="17"/>
        </svg>
        <span style="flex:1;font-size:14px;font-weight:600">{{ d.filename }}</span>
        <span style="font-size:12px;color:var(--text-tertiary)">{{ formatSize(d.fileSize) }}</span>
        <span
            style="font-size:11px;padding:2px 8px;border-radius:20px;background:var(--accent-subtle);color:var(--accent);font-weight:600">{{
            d.status === 2 ? '已向量化' : d.status === 1 ? '已解析' : '待处理'
          }}</span>
        <a v-if="d.status===2&&downloadUrl(d)" :href="downloadUrl(d)"
           style="font-size:11px;color:var(--accent);text-decoration:none;font-weight:600">下载</a>
        <button class="doc-card-del" title="删除" type="button" @click="deleteDoc(d.id)">
          <svg fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
            <line x1="18" x2="6" y1="6" y2="18"/>
            <line x1="6" x2="18" y1="6" y2="18"/>
          </svg>
        </button>
      </div>
    </div>
    <div v-else style="padding:40px;text-align:center;color:var(--text-tertiary);font-size:14px">暂无文档，上传
      PDF/Markdown/TXT/DOCX
    </div>
  </div>
</template>

<style scoped>
.kbase-upload-accent {
  background: var(--accent);
  color: #fff
}
</style>
