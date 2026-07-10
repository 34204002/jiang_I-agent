<script lang="ts" setup>
import {nextTick, onMounted, ref} from 'vue'
import {state} from '../stores/state'
import {api} from '../utils/api'
import {showToast} from '../utils/toast'
import {Network} from 'vis-network'
import {DataSet} from 'vis-data'
import type {Concept, ConceptDetail, GraphPayload, PageResult} from '../types'
import CloseIcon from './icons/CloseIcon.vue'
import FileIcon from './icons/FileIcon.vue'

const concepts = ref<Concept[]>([])
const adding = ref(false), newName = ref(''), newDesc = ref(''), newCat = ref(''), newDiff = ref(3)
const detail = ref<ConceptDetail | null>(null)
const graphRelFilter = ref<'all' | 'prereq' | 'related'>('prereq')
let graphNetwork: any = null
let currentGraphData: GraphPayload | null = null

const COLORS: Record<string, string> = {
  '中间件': '#d97706',
  '编程语言': '#2563eb',
  '数据库': '#059669',
  '算法': '#7c3aed',
  '网络': '#dc2626',
  '操作系统': '#0891b2',
  '架构': '#ea580c',
  '前端': '#db2777',
  '安全': '#b91c1c',
  '其他': '#4b5563'
}

async function search() {
  const p = state.graphKeyword ? `keyword=${encodeURIComponent(state.graphKeyword)}&` : ''
  const c = state.graphCategory ? `category=${encodeURIComponent(state.graphCategory)}&` : ''
  const json = await api.get<PageResult<Concept>>(`/api/graph/concepts?${p}${c}page=${state.graphPage}`)
  if (json.code === 200 && json.data) concepts.value = json.data.records || []
}

async function doAdd() {
  if (!newName.value.trim()) return showToast('概念名称不能为空', 'error')
  await api.post('/api/graph/concepts', {
    name: newName.value.trim(),
    description: newDesc.value,
    category: newCat.value || '其他',
    difficulty: newDiff.value
  })
  adding.value = false;
  newName.value = '';
  search()
}

async function showDetail(name: string) {
  const json = await api.get<ConceptDetail>(`/api/graph/concepts/${encodeURIComponent(name)}`)
  if (json.code !== 200) return
  detail.value = json.data
}

async function toggleGraphView() {
  state.graphViewMode = !state.graphViewMode
  if (state.graphViewMode) {
    await nextTick()
    if (!state.graphKeyword.trim()) {
      state.graphViewMode = false;
      return showToast('请先输入概念名称', 'error')
    }
    const json = await api.get<GraphPayload>(`/api/graph/concepts/${encodeURIComponent(state.graphKeyword.trim())}/graph`)
    if (json.code !== 200 || !json.data.nodes.length) {
      state.graphViewMode = false;
      return showToast('未找到', 'error')
    }
    renderGraph(json.data)
  }
}

function renderGraph(data: GraphPayload) {
  currentGraphData = data  // 保存以便过滤切换
  const el = document.getElementById('graphNetContainer')
  if (!el) return;
  if (graphNetwork) graphNetwork.destroy()
  const filter = graphRelFilter.value

  const nodeOpts = (cat: string, center: boolean) => {
    const bg = center ? '#ec4899' : (COLORS[cat] || '#4b5563')
    return {
      color: {
        background: bg,
        border: bg,
        highlight: {background: bg, border: bg},
        hover: {background: bg, border: bg}
      },
      font: {color: '#fff', size: 13, face: 'Inter', bold: {color: '#fff', size: 13, face: 'Inter', mod: 'bold'}},
      borderWidth: 0,
      shape: 'box',
      shapeProperties: {borderRadius: 8},
      margin: {top: 8, right: 14, bottom: 8, left: 14},
      shadow: {enabled: true, color: 'rgba(0,0,0,.12)', size: 4, x: 0, y: 1}
    }
  }

  const nodes = new DataSet<any>(data.nodes.map((n: {
    id: string;
    label: string;
    level?: number;
    category?: string;
    center?: boolean
  }) => {
    const isCenter = n.center === true
    return {id: n.id, label: n.id, level: n.level, ...nodeOpts(n.category || '其他', isCenter)}
  }))

  const existingEdges = new Set<string>()
  const addEdgeSet = (from: string, to: string, label: string) => existingEdges.add(`${from}|||${to}|||${label}`)
  const hasEdge = (from: string, to: string, label: string) => existingEdges.has(`${from}|||${to}|||${label}`)

  // 关系类型过滤
  const edgeVisible = (label: string) => {
    if (filter === 'prereq') return label === 'PREREQUISITE_OF'
    if (filter === 'related') return label === 'RELATED_TO'
    return true
  }
  const edges = new DataSet<any>(data.edges.filter((e: { label: string }) => edgeVisible(e.label)).map((e: {
    from: string;
    to: string;
    label: string
  }, i: number) => {
    addEdgeSet(e.from, e.to, e.label)
    return {
      id: i, from: e.from, to: e.to,
      label: e.label === 'PREREQUISITE_OF' ? '← 前置' : e.label === 'RELATED_TO' ? '相关' : e.label,
      arrows: {to: {enabled: true, scaleFactor: 0.6}},
      smooth: {enabled: true, type: 'cubicBezier', forceDirection: 'horizontal', roundness: 0.4},
      color: {
        color: e.label === 'PREREQUISITE_OF' ? '#7c3aed' : '#cbd5e1',
        highlight: e.label === 'PREREQUISITE_OF' ? '#6d28d9' : '#94a3b8'
      },
      font: {size: 10, color: '#64748b', strokeWidth: 2, strokeColor: '#fff', align: 'middle'},
      width: e.label === 'PREREQUISITE_OF' ? 2.5 : 1.5,
      dashes: e.label === 'RELATED_TO'
    }
  }))

  graphNetwork = new Network(el, {nodes, edges}, {
    layout: {
      hierarchical: {
        enabled: true,
        direction: 'LR',
        sortMethod: 'directed',
        nodeSpacing: 120,
        levelSeparation: 220,
        treeSpacing: 80,
        shakeTowards: 'roots'
      }
    },
    physics: {enabled: false},
    interaction: {
      hover: true,
      zoomView: true,
      dragView: true,
      navigationButtons: false,
      dragNodes: true
    },
    edges: {
      smooth: {enabled: true, type: 'cubicBezier', forceDirection: 'horizontal', roundness: 0.4}
    }
  })

  graphNetwork.on('doubleClick', (p: { nodes: string[] }) => {
    if (!p.nodes.length) return
    api.get<GraphPayload>(`/api/graph/concepts/${encodeURIComponent(p.nodes[0])}/graph`)
        .then((json) => {
          if (json.code !== 200 || !json.data) return
          const newNodes: any[] = [], newEdges: any[] = []
          ;(json.data.nodes || []).forEach((n: { id: string; label: string; category?: string }) => {
            if (nodes.getIds().indexOf(n.id) === -1) newNodes.push({
              id: n.id,
              label: n.id, ...nodeOpts(n.category || '其他', false)
            })
          })
          ;(json.data.edges || []).forEach((e: { from: string; to: string; label: string }) => {
            if (!hasEdge(e.from, e.to, e.label)) {
              addEdgeSet(e.from, e.to, e.label)
              newEdges.push({
                id: `e_${e.from}_${e.to}`, from: e.from, to: e.to,
                label: e.label === 'PREREQUISITE_OF' ? '← 前置' : e.label === 'RELATED_TO' ? '相关' : e.label,
                arrows: {to: {enabled: true, scaleFactor: 0.6}},
                smooth: {enabled: true, type: 'cubicBezier', forceDirection: 'horizontal', roundness: 0.4},
                color: {
                  color: e.label === 'PREREQUISITE_OF' ? '#7c3aed' : '#cbd5e1',
                  highlight: e.label === 'PREREQUISITE_OF' ? '#6d28d9' : '#94a3b8'
                },
                font: {size: 10, color: '#64748b', strokeWidth: 2, strokeColor: '#fff', align: 'middle'},
                width: e.label === 'PREREQUISITE_OF' ? 2.5 : 1.5,
                dashes: e.label === 'RELATED_TO'
              })
            }
          })
          if (newNodes.length || newEdges.length) {
            if (newNodes.length) nodes.add(newNodes)
            if (newEdges.length) edges.add(newEdges)
            // 短暂启动物理引擎重新布局，之后回到静态层次
            graphNetwork.setOptions({physics: {enabled: true, solver: 'hierarchicalRepulsion'}})
            setTimeout(() => graphNetwork.setOptions({physics: {enabled: false}}), 1200)
          }
        })
  })

}

function refilterGraph() {
  if (currentGraphData) renderGraph(currentGraphData)
}

async function deleteConcept(name: string) {
  if (!confirm(`确定删除概念 "${name}" 及其所有关系？`)) return
  await api.del(`/api/graph/concepts/${encodeURIComponent(name)}`)
  showToast(`已删除概念: ${name}`, 'ok')
  concepts.value = concepts.value.filter(c => c.name !== name)
}

onMounted(search)
</script>

<template>
  <div class="kbase-panel">
    <!-- Toolbar -->
    <div class="kbase-toolbar">
      <input v-model="state.graphKeyword" class="kbase-search-input" placeholder="搜索概念…" @keydown.enter="search">
      <select v-model="state.graphCategory" class="kbase-search-input graph-cat-select">
        <option value="">全部分类</option>
      </select>
      <button class="kbase-search-btn" type="button" @click="search">搜索</button>
      <button class="kbase-search-btn graph-btn-accent" type="button" @click="adding=!adding">+ 添加</button>
      <button class="kbase-search-btn graph-btn-view" type="button" @click="toggleGraphView">
        {{ state.graphViewMode ? '列表' : '图视图' }}
      </button>
    </div>

    <!-- Add form -->
    <div v-if="adding" class="graph-add-form">
      <div class="graph-add-grid">
        <input v-model="newName" class="kbase-search-input graph-name-input" placeholder="概念名称 *">
        <input v-model="newDesc" class="kbase-search-input" placeholder="描述">
        <select v-model="newCat" class="kbase-search-input">
          <option value="">分类</option>
          <option>中间件</option>
          <option>编程语言</option>
          <option>数据库</option>
          <option>算法</option>
          <option>网络</option>
          <option>操作系统</option>
          <option>架构</option>
          <option>前端</option>
          <option>安全</option>
          <option>其他</option>
        </select>
        <select v-model="newDiff" class="kbase-search-input">
          <option :value="1">入门</option>
          <option :value="2">基础</option>
          <option :value="3">中级</option>
          <option :value="4">进阶</option>
          <option :value="5">专家</option>
        </select>
      </div>
      <div class="graph-add-actions">
        <button class="kbase-search-btn" type="button" @click="adding=false">取消</button>
        <button class="kbase-search-btn graph-btn-accent" type="button" @click="doAdd">保存</button>
      </div>
    </div>

    <!-- Graph view -->
    <div v-if="state.graphViewMode" class="graph-filter-bar">
      <button :class="{ active: graphRelFilter==='prereq' }" class="graph-filter-btn" type="button"
              @click="graphRelFilter='prereq'; refilterGraph()">仅前置
      </button>
      <button :class="{ active: graphRelFilter==='related' }" class="graph-filter-btn" type="button"
              @click="graphRelFilter='related'; refilterGraph()">仅相关
      </button>
      <button :class="{ active: graphRelFilter==='all' }" class="graph-filter-btn" type="button"
              @click="graphRelFilter='all'; refilterGraph()">全部
      </button>
      <button class="graph-reset-btn" title="重置视图"
              type="button" @click="graphNetwork && graphNetwork.fit({ animation: true })">
        <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
          <circle cx="12" cy="12" r="10"/>
          <polyline points="8 12 12 8 16 12"/>
          <line x1="12" x2="12" y1="16" y2="8"/>
        </svg>
      </button>
    </div>
    <div v-if="state.graphViewMode" id="graphNetContainer" class="graph-net"></div>

    <!-- List view -->
    <div v-if="!state.graphViewMode" class="doc-list">
      <div v-for="c in concepts" :key="c.name" class="graph-concept-card" @click="showDetail(c.name)">
        <span class="graph-concept-name">{{ c.name }}</span><span class="graph-cat-tag">{{ c.category }}</span>
        <button class="graph-concept-del" title="删除概念" type="button" @click.stop="deleteConcept(c.name)">
          <CloseIcon :size="12"/>
        </button>
        <span class="graph-concept-rel">{{ c.relationCount }} 关系</span>
        <div class="graph-concept-desc">{{ c.description }}</div>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="!state.graphViewMode" class="graph-pager">
      <button :disabled="state.graphPage<=1" class="kbase-search-btn" type="button" @click="state.graphPage--;search()">
        上一页
      </button>
      <span class="graph-page-num">{{ state.graphPage }}</span>
      <button class="kbase-search-btn" type="button" @click="state.graphPage++;search()">下一页</button>
    </div>

    <!-- Detail modal (Teleported to body) -->
    <Teleport to="body">
      <div v-if="detail" class="graph-modal-overlay" @click.self="detail=null">
        <div class="graph-modal">
          <button class="graph-modal-close" type="button" @click="detail=null">
            <CloseIcon :size="12"/>
          </button>
          <h2>{{ detail.name }} <span class="graph-cat-tag">{{ detail.category || '' }}</span> {{
              detail.difficulty || 1
            }}</h2>
          <p class="graph-detail-desc">{{ detail.description || '' }}</p>
          <template v-if="detail.prerequisites?.length">
            <div class="graph-section-title">前置知识</div>
            <div class="graph-chip-row">
              <span v-for="p in detail.prerequisites" :key="p.name" class="graph-ref-chip">{{ p.name }} {{
                  p.difficulty
                }}</span>
            </div>
          </template>
          <template v-if="detail.related?.length">
            <div class="graph-section-title">相关概念</div>
            <div class="graph-chip-row">
              <span v-for="r in detail.related" :key="r.name" class="graph-ref-chip">{{ r.name }}</span>
            </div>
          </template>
          <template v-if="detail.documents?.length">
            <div class="graph-section-title">关联文档</div>
            <div v-for="dc in detail.documents" :key="dc.filename" class="graph-doc-item">
              <FileIcon :size="14"/>
              {{ dc.filename }}
            </div>
          </template>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* ---- Toolbar extras ---- */
.graph-cat-select {
  max-width: 140px
}

.graph-btn-accent {
  background: var(--accent);
  color: #fff
}

.graph-btn-view {
  margin-left: auto
}

/* ---- Add form ---- */
.graph-add-form {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
}

.graph-add-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.graph-add-actions {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

/* ---- Graph network ---- */
.graph-net {
  width: 100%;
  height: 450px;
  border-radius: 12px;
  margin-bottom: 16px;
  background: #fafafa;
  border: 1px solid var(--border);
}

/* ---- Concept card ---- */
.graph-concept-card {
  padding: 12px 16px;
  background: #fff;
  border-radius: 10px;
  margin-bottom: 6px;
  cursor: pointer;
  border: 1px solid var(--border);
}

.graph-concept-name {
  font-weight: 700
}

.graph-concept-rel {
  font-size: 12px;
  color: var(--text-secondary);
  float: right
}

.graph-concept-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px
}

/* ---- Pager ---- */
.graph-pager {
  display: flex;
  gap: 8px;
  justify-content: center;
  padding: 12px 0;
}

.graph-page-num {
  line-height: 32px;
  font-size: 13px;
  color: var(--text-secondary)
}

/* ---- Detail modal ---- */
.graph-detail-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px
}

.graph-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px
}

.graph-doc-item {
  font-size: 13px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ---- Filter bar ---- */
.graph-filter-bar {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 8px 0;
  margin-bottom: 4px;
}

.graph-filter-btn {
  padding: 5px 14px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  border: 1.5px solid var(--border);
  background: var(--bg-surface);
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all .2s;
}

.graph-filter-btn:hover {
  border-color: var(--accent-light);
  color: var(--accent)
}

.graph-filter-btn.active {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}

/* ---- Concept delete ---- */
.graph-concept-del {
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
  margin-right: 8px;
}

.graph-concept-del:hover {
  background: #FEE2E2;
  color: #EF4444
}

.graph-name-input {
  grid-column: 1 / -1
}

.graph-reset-btn {
  margin-left: auto
}
</style>
