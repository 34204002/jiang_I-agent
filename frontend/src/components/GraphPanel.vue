<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { state } from '../stores/state'
import { api } from '../utils/api'
import { showToast } from '../utils/toast'
import { Network } from 'vis-network'
import { DataSet } from 'vis-data'

const concepts = ref([])
const adding = ref(false), newName = ref(''), newDesc = ref(''), newCat = ref(''), newDiff = ref(3)
const pathFrom = ref(''), pathTo = ref('')
const pathResults = ref([])
const detail = ref(null)
const graphRelFilter = ref('prereq')  // 'all' | 'prereq' | 'related'
let graphNetwork = null
let currentGraphData = null

const COLORS = { '中间件':'#d97706','编程语言':'#2563eb','数据库':'#059669','算法':'#7c3aed','网络':'#dc2626','操作系统':'#0891b2','架构':'#ea580c','前端':'#db2777','安全':'#b91c1c','其他':'#4b5563' }

async function search() {
  const p = state.graphKeyword ? `keyword=${encodeURIComponent(state.graphKeyword)}&` : ''
  const c = state.graphCategory ? `category=${encodeURIComponent(state.graphCategory)}&` : ''
  const json = await api.get(`/api/graph/concepts?${p}${c}page=${state.graphPage}`)
  if (json.code===200 && json.data) concepts.value = json.data.records || []
}

async function doAdd() {
  if (!newName.value.trim()) return showToast('概念名称不能为空', 'error')
  await api.post('/api/graph/concepts', { name:newName.value.trim(), description:newDesc.value, category:newCat.value||'其他', difficulty:newDiff.value })
  adding.value = false; newName.value = ''; search()
}

async function showDetail(name) {
  const json = await api.get(`/api/graph/concepts/${encodeURIComponent(name)}`)
  if (json.code!==200) return
  detail.value = json.data
}

async function findPath() {
  if (!pathFrom.value||!pathTo.value) return showToast('请输入起始和目标概念', 'error')
  const json = await api.get(`/api/graph/concepts/${encodeURIComponent(pathFrom.value)}/path?target=${encodeURIComponent(pathTo.value)}&maxHops=5`)
  if (json.code!==200) return
  if (!json.data.paths?.length) { pathResults.value = []; showToast('未找到路径', 'info'); return }
  pathResults.value = json.data.paths
}

async function toggleGraphView() {
  state.graphViewMode = !state.graphViewMode
  if (state.graphViewMode) {
    await nextTick()
    if (!state.graphKeyword.trim()) { state.graphViewMode = false; return showToast('请先输入概念名称', 'error') }
    const json = await api.get(`/api/graph/concepts/${encodeURIComponent(state.graphKeyword.trim())}/graph`)
    if (json.code!==200||!json.data.nodes.length) { state.graphViewMode = false; return showToast('未找到', 'error') }
    renderGraph(json.data)
  }
}

function renderGraph(data) {
  currentGraphData = data  // 保存以便过滤切换
  const el = document.getElementById('graphNetContainer')
  if (!el) return; if (graphNetwork) graphNetwork.destroy()
  const filter = graphRelFilter.value

  const nodeOpts = (cat, center) => {
    const bg = center ? '#ec4899' : (COLORS[cat] || '#4b5563')
    return {
      color: {
        background: bg,
        border: center ? '#be185d' : '#1e293b',
        highlight: { background: bg, border: '#000' },
        hover: { background: bg, border: '#000' }
      },
      font: { color: '#fff', size: 13, face: 'Inter', bold: { color: '#fff', size: 13, face: 'Inter', mod: 'bold' } },
      borderWidth: center ? 3 : 2,
      shape: 'box',
      shapeProperties: { borderRadius: 8 },
      margin: { top: 8, right: 14, bottom: 8, left: 14 },
      shadow: { enabled: true, color: 'rgba(0,0,0,.15)', size: 6, x: 0, y: 2 },
      fixed: center ? { x: true, y: true } : false  // keep center node in position
    }
  }

  const nodes = new DataSet(data.nodes.map(n => {
    const isCenter = n.center === true
    return { id: n.id, label: n.id, level: n.level, ...nodeOpts(n.category, isCenter) }
  }))

  const existingEdges = new Set()
  const addEdgeSet = (from, to, label) => existingEdges.add(`${from}|||${to}|||${label}`)
  const hasEdge = (from, to, label) => existingEdges.has(`${from}|||${to}|||${label}`)

  // 关系类型过滤
  const edgeVisible = (label) => {
    if (filter === 'prereq') return label === 'PREREQUISITE_OF'
    if (filter === 'related') return label === 'RELATED_TO'
    return true
  }
  const edges = new DataSet(data.edges.filter(e => edgeVisible(e.label)).map((e, i) => {
    addEdgeSet(e.from, e.to, e.label)
    return {
      id: i, from: e.from, to: e.to,
      label: e.label === 'PREREQUISITE_OF' ? '← 前置' : e.label === 'RELATED_TO' ? '相关' : e.label,
      arrows: { to: { enabled: true, scaleFactor: 0.6 } },
      smooth: { type: 'cubicBezier', forceDirection: 'horizontal' },
      color: {
        color: e.label === 'PREREQUISITE_OF' ? '#7c3aed' : '#cbd5e1',
        highlight: e.label === 'PREREQUISITE_OF' ? '#6d28d9' : '#94a3b8'
      },
      font: { size: 10, color: '#64748b', strokeWidth: 2, strokeColor: '#fff', align: 'middle' },
      width: e.label === 'PREREQUISITE_OF' ? 2.5 : 1.5,
      dashes: e.label === 'RELATED_TO'
    }
  }))

  graphNetwork = new Network(el, { nodes, edges }, {
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
    physics: { enabled: false },
    interaction: {
      hover: true,
      zoomView: true,
      dragView: true,
      navigationButtons: false,
      dragNodes: true
    },
    edges: {
      smooth: { type: 'cubicBezier', forceDirection: 'horizontal' }
    }
  })

  graphNetwork.on('doubleClick', p => {
    if (!p.nodes.length) return
    fetch(`/api/graph/concepts/${encodeURIComponent(p.nodes[0])}/graph`)
      .then(r => r.json())
      .then(res => {
        if (res.code !== 200) return
        const newNodes = [], newEdges = []
        ;(res.data.nodes || []).forEach(n => {
          if (nodes.getIds().indexOf(n.id) === -1) newNodes.push({ id: n.id, label: n.id, ...nodeOpts(n.category, false) })
        })
        ;(res.data.edges || []).forEach(e => {
          if (!hasEdge(e.from, e.to, e.label)) {
            addEdgeSet(e.from, e.to, e.label)
            newEdges.push({
              id: `e_${e.from}_${e.to}`, from: e.from, to: e.to,
              label: e.label === 'PREREQUISITE_OF' ? '← 前置' : e.label === 'RELATED_TO' ? '相关' : e.label,
              arrows: { to: { enabled: true, scaleFactor: 0.6 } },
              smooth: { type: 'cubicBezier', forceDirection: 'horizontal' },
              color: { color: e.label === 'PREREQUISITE_OF' ? '#7c3aed' : '#cbd5e1', highlight: e.label === 'PREREQUISITE_OF' ? '#6d28d9' : '#94a3b8' },
              font: { size: 10, color: '#64748b', strokeWidth: 2, strokeColor: '#fff', align: 'middle' },
              width: e.label === 'PREREQUISITE_OF' ? 2.5 : 1.5,
              dashes: e.label === 'RELATED_TO'
            })
          }
        })
        if (newNodes.length) { nodes.add(newNodes); graphNetwork.storePositions() }
        if (newEdges.length) edges.add(newEdges)
      })
  })

  graphNetwork.on('stabilized', () => { graphNetwork.storePositions() })
}

function refilterGraph() { if (currentGraphData) renderGraph(currentGraphData) }

async function deleteConcept(name) {
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
    <input class="kbase-search-input" v-model="state.graphKeyword" @keydown.enter="search" placeholder="搜索概念…">
    <select class="kbase-search-input graph-cat-select" v-model="state.graphCategory"><option value="">全部分类</option></select>
    <button type="button" class="kbase-search-btn" @click="search">搜索</button>
    <button type="button" class="kbase-search-btn graph-btn-accent" @click="adding=!adding">+ 添加</button>
    <button type="button" class="kbase-search-btn graph-btn-view" @click="toggleGraphView">{{ state.graphViewMode ? '列表' : '图视图' }}</button>
  </div>

  <!-- Add form -->
  <div v-if="adding" class="graph-add-form">
    <div class="graph-add-grid">
      <input v-model="newName" placeholder="概念名称 *" class="kbase-search-input" style="grid-column:1/-1">
      <input v-model="newDesc" placeholder="描述" class="kbase-search-input">
      <select v-model="newCat" class="kbase-search-input"><option value="">分类</option><option>中间件</option><option>编程语言</option><option>数据库</option><option>算法</option><option>网络</option><option>操作系统</option><option>架构</option><option>前端</option><option>安全</option><option>其他</option></select>
      <select v-model="newDiff" class="kbase-search-input"><option :value="1">入门</option><option :value="2">基础</option><option :value="3">中级</option><option :value="4">进阶</option><option :value="5">专家</option></select>
    </div>
    <div class="graph-add-actions">
      <button type="button" class="kbase-search-btn" @click="adding=false">取消</button>
      <button type="button" class="kbase-search-btn graph-btn-accent" @click="doAdd">保存</button>
    </div>
  </div>

  <!-- Path finder -->
  <div class="graph-path-bar">
    <span class="graph-path-label">学习路径:</span>
    <input class="kbase-search-input graph-path-input" v-model="pathFrom" placeholder="起始概念">
    <span>→</span>
    <input class="kbase-search-input graph-path-input" v-model="pathTo" placeholder="目标概念">
    <button type="button" class="kbase-search-btn" @click="findPath">查询</button>
  </div>

  <!-- Path results -->
  <div v-if="pathResults.length" class="graph-path-results">
    <div v-for="(p, i) in pathResults" :key="i" class="graph-path-chain">
      <span class="graph-path-label">路径{{ i+1 }}</span>
      <template v-for="(n, j) in p" :key="j">
        <span v-if="j>0" class="graph-path-arrow">→</span>
        <span class="graph-path-node">{{ n.name }} {{ n.difficulty }}</span>
      </template>
    </div>
  </div>

  <!-- Graph view -->
  <div v-if="state.graphViewMode" class="graph-filter-bar">
    <button type="button" class="graph-filter-btn" :class="{ active: graphRelFilter==='prereq' }" @click="graphRelFilter='prereq'; refilterGraph()">仅前置</button>
    <button type="button" class="graph-filter-btn" :class="{ active: graphRelFilter==='related' }" @click="graphRelFilter='related'; refilterGraph()">仅相关</button>
    <button type="button" class="graph-filter-btn" :class="{ active: graphRelFilter==='all' }" @click="graphRelFilter='all'; refilterGraph()">全部</button>
    <span style="font-size:11px;color:var(--text-tertiary);margin-left:auto">双击节点展开</span>
  </div>
  <div v-if="state.graphViewMode" id="graphNetContainer" class="graph-net"></div>

  <!-- List view -->
  <div v-if="!state.graphViewMode" class="doc-list">
    <div v-for="c in concepts" :key="c.name" class="graph-concept-card" @click="showDetail(c.name)">
      <span class="graph-concept-name">{{ c.name }}</span><span class="graph-cat-tag">{{ c.category }}</span>
      <button type="button" class="graph-concept-del" @click.stop="deleteConcept(c.name)" title="删除概念"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
      <span class="graph-concept-rel">{{ c.relationCount }} 关系</span>
      <div class="graph-concept-desc">{{ c.description }}</div>
    </div>
  </div>

  <!-- Pagination -->
  <div v-if="!state.graphViewMode" class="graph-pager">
    <button type="button" class="kbase-search-btn" :disabled="state.graphPage<=1" @click="state.graphPage--;search()">上一页</button>
    <span class="graph-page-num">{{ state.graphPage }}</span>
    <button type="button" class="kbase-search-btn" @click="state.graphPage++;search()">下一页</button>
  </div>

  <!-- Detail modal (Teleported to body) -->
  <Teleport to="body">
    <div v-if="detail" class="graph-modal-overlay" @click.self="detail=null">
      <div class="graph-modal">
        <button type="button" class="graph-modal-close" @click="detail=null">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
        <h2>{{ detail.name }} <span class="graph-cat-tag">{{ detail.category || '' }}</span> {{ detail.difficulty || 1 }}</h2>
        <p class="graph-detail-desc">{{ detail.description || '' }}</p>
        <template v-if="detail.prerequisites?.length">
          <div class="graph-section-title">前置知识</div>
          <div class="graph-chip-row">
            <span v-for="p in detail.prerequisites" :key="p.name" class="graph-ref-chip">{{ p.name }} {{ p.difficulty }}</span>
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
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
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
.graph-cat-select { max-width: 140px }
.graph-btn-accent { background: var(--accent); color: #fff }
.graph-btn-view { margin-left: auto }

/* ---- Add form ---- */
.graph-add-form {
  background: #fff; border-radius: 12px; padding: 20px; margin-bottom: 16px;
}
.graph-add-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px;
}
.graph-add-actions {
  margin-top: 14px; display: flex; gap: 8px; justify-content: flex-end;
}

/* ---- Path bar ---- */
.graph-path-bar {
  display: flex; gap: 10px; align-items: center; margin-bottom: 16px;
  padding: 12px 16px; background: #fff; border-radius: 12px; flex-wrap: wrap;
}
.graph-path-label { font-size: 13px; font-weight: 600 }
.graph-path-input { max-width: 150px }

/* ---- Path results ---- */
.graph-path-results { margin-bottom: 16px }

/* ---- Graph network ---- */
.graph-net {
  width: 100%; height: 450px; border-radius: 12px; margin-bottom: 16px;
  background: #fafafa; border: 1px solid var(--border);
}

/* ---- Concept card ---- */
.graph-concept-card {
  padding: 12px 16px; background: #fff; border-radius: 10px; margin-bottom: 6px;
  cursor: pointer; border: 1px solid var(--border);
}
.graph-concept-name { font-weight: 700 }
.graph-concept-rel { font-size: 12px; color: var(--text-secondary); float: right }
.graph-concept-desc { font-size: 12px; color: var(--text-tertiary); margin-top: 4px }

/* ---- Pager ---- */
.graph-pager {
  display: flex; gap: 8px; justify-content: center; padding: 12px 0;
}
.graph-page-num { line-height: 32px; font-size: 13px; color: var(--text-secondary) }

/* ---- Detail modal ---- */
.graph-detail-desc { font-size: 14px; color: var(--text-secondary); margin-bottom: 8px }
.graph-chip-row { display: flex; flex-wrap: wrap; gap: 6px }
.graph-doc-item {
  font-size: 13px; color: var(--text-secondary);
  display: flex; align-items: center; gap: 6px;
}

/* ---- Filter bar ---- */
.graph-filter-bar {
  display: flex; gap: 6px; align-items: center;
  padding: 8px 0; margin-bottom: 4px;
}
.graph-filter-btn {
  padding: 5px 14px; border-radius: 20px; font-size: 12px; font-weight: 600;
  border: 1.5px solid var(--border); background: var(--bg-surface);
  color: var(--text-tertiary); cursor: pointer; transition: all .2s;
}
.graph-filter-btn:hover { border-color: var(--accent-light); color: var(--accent) }
.graph-filter-btn.active {
  background: var(--accent); color: #fff; border-color: var(--accent);
}

/* ---- Concept delete ---- */
.graph-concept-del {
  width: 26px; height: 26px; display: flex; align-items: center;
  justify-content: center; border-radius: 6px; color: var(--text-tertiary);
  cursor: pointer; transition: all .15s; flex-shrink: 0;
  margin-left: auto; margin-right: 8px;
}
.graph-concept-del:hover { background: #FEE2E2; color: #EF4444 }
</style>
