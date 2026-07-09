<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {api} from '../utils/api'
import {showToast} from '../utils/toast'
import type {DocumentItem, PageResult, SearchResponse} from '../types'
import FileIcon from './icons/FileIcon.vue'
import CloseIcon from './icons/CloseIcon.vue'

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
  const json = await api.postForm('/api/knowledge/documents', form)
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
  <div class="kb-panel">
    <!-- Upload + Search toolbar -->
    <div class="kbase-toolbar">
      <input v-model="searchQuery" class="kbase-search-input" placeholder="搜索知识库…" @keydown.enter="search">
      <button class="kbase-search-btn" type="button" @click="search">搜索</button>
      <input ref="fileInput" accept=".pdf,.md,.txt,.docx" class="kb-file-input" type="file" @change="uploadDoc">
      <button class="kbase-search-btn kbase-upload-accent" type="button" @click="fileInput?.click()">上传文档</button>
    </div>

    <!-- Search result -->
    <div v-if="searchResult" class="kb-search-result">
      <div class="kb-search-header">
        <span class="kb-search-title">搜索结果</span>
        <button class="kbase-search-btn kb-search-clear" type="button" @click="searchResult=null">清除</button>
      </div>
      <div class="kb-search-answer">{{ searchResult.answer }}</div>
      <div v-for="(s,i) in searchResult.sources" :key="i" class="kb-search-source">
        <span class="kb-search-source-name">
          <svg class="kb-svg-link-icon" fill="none" height="12" stroke="currentColor" stroke-width="2"
               viewBox="0 0 24 24" width="12"><path
              d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>
          {{ s.filename }}
        </span>
        <span class="kb-search-score">相关度 {{ Math.round(s.score * 100) }}%</span>
        <div class="kb-search-snippet">{{ s.content?.substring(0, 200) }}{{ s.content?.length > 200 ? '…' : '' }}</div>
      </div>
    </div>

    <!-- Document list -->
    <div class="kb-list-header">文档列表 ({{ docs.length }})</div>
    <div v-if="docs.length" class="doc-list">
      <div v-for="d in docs" :key="d.id" class="kb-doc-item">
        <FileIcon v-if="d.fileType==='pdf'" :color="'#EF4444'" />
        <FileIcon v-else-if="d.fileType==='md'" :color="'#3B82F6'" />
        <FileIcon v-else :color="'#8B5CF6'" />
        <span class="kb-doc-name">{{ d.filename }}</span>
        <span class="kb-doc-size">{{ formatSize(d.fileSize) }}</span>
        <span class="kb-doc-status">{{ d.status === 2 ? '已向量化' : d.status === 1 ? '已解析' : '待处理' }}</span>
        <a v-if="d.status===2&&downloadUrl(d)" :href="downloadUrl(d)" class="kb-doc-download">下载</a>
        <button class="kb-doc-del" title="删除" type="button" @click="deleteDoc(d.id)">
          <CloseIcon :size="14" />
        </button>
      </div>
    </div>
    <div v-else class="kb-list-empty">暂无文档，上传 PDF/Markdown/TXT/DOCX</div>
  </div>
</template>

<style scoped>
.kb-panel {
  overflow-y: auto
}

.kb-file-input {
  display: none
}

.kbase-upload-accent {
  background: var(--accent);
  color: #fff
}

/* search result */
.kb-search-result {
  margin-bottom: 16px
}

.kb-search-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px
}

.kb-search-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--accent)
}

.kb-search-clear {
  font-size: 11px;
  padding: 2px 8px
}

.kb-search-answer {
  font-size: 14px;
  line-height: 1.7;
  padding: 16px;
  background: var(--bg-surface, #fff);
  border-radius: 12px;
  margin-bottom: 10px
}

.kb-search-source {
  padding: 10px 14px;
  background: #fff;
  border-radius: 10px;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--text-secondary)
}

.kb-search-source-name {
  font-weight: 700;
  color: var(--accent)
}

.kb-svg-link-icon {
  vertical-align: middle;
  margin-right: 3px
}

.kb-search-score {
  margin-left: 8px;
  font-size: 11px;
  color: var(--text-tertiary)
}

.kb-search-snippet {
  margin-top: 4px;
  color: var(--text-secondary)
}

/* doc list */
.kb-list-header {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 8px
}

.kb-doc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 10px;
  margin-bottom: 6px;
  border: 1px solid var(--border)
}

.kb-doc-name {
  flex: 1;
  font-size: 14px;
  font-weight: 600
}

.kb-doc-size {
  font-size: 12px;
  color: var(--text-tertiary)
}

.kb-doc-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 20px;
  background: var(--accent-subtle);
  color: var(--accent);
  font-weight: 600
}

.kb-doc-download {
  font-size: 11px;
  color: var(--accent);
  text-decoration: none;
  font-weight: 600
}

.kb-doc-del {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all .15s;
  flex-shrink: 0;
  margin-left: auto;
  border: none;
  background: none
}

.kb-doc-del:hover {
  background: #FEE2E2;
  color: #EF4444
}

.kb-list-empty {
  padding: 40px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 14px
}
</style>
