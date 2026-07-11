<script lang="ts" setup>
import {onMounted, ref, shallowRef} from 'vue'
import {state} from '../stores/state'
import {api} from '../utils/api'
import {showToast} from '../utils/toast'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {GraphChart} from 'echarts/charts'
import {TooltipComponent, LegendComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import type {Concept, ConceptDetail, GraphEdge, GraphNode, GraphPayload, PageResult} from '../types'
import CloseIcon from './icons/CloseIcon.vue'
import FileIcon from './icons/FileIcon.vue'

use([GraphChart, TooltipComponent, LegendComponent, CanvasRenderer])

const concepts = ref<Concept[]>([])
const adding = ref(false), newName = ref(''), newDesc = ref(''), newCat = ref(''), newDiff = ref(3)
const detail = ref<ConceptDetail | null>(null)
const graphRelFilter = ref<'all' | 'prereq' | 'related'>('prereq')

const graphOption = shallowRef<Record<string, unknown>>({})
const graphData = ref<GraphPayload | null>(null)
const chartLoading = ref(false)

const RELATION_TYPES = {
  PREREQUISITE: 'PREREQUISITE_OF',
  RELATED: 'RELATED_TO',
} as const

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
  adding.value = false; newName.value = ''; search()
}

async function showDetail(name: string) {
  const json = await api.get<ConceptDetail>(`/api/graph/concepts/${encodeURIComponent(name)}`)
  if (json.code !== 200) return
  detail.value = json.data
}

// ==================== ECharts Graph ====================

function edgesVisible(e: GraphEdge): boolean {
  if (graphRelFilter.value === 'prereq') return e.label === RELATION_TYPES.PREREQUISITE
  if (graphRelFilter.value === 'related') return e.label === RELATION_TYPES.RELATED
  return true
}

function buildOption(data: GraphPayload) {
  // 建 nodes/links
  const categories = Object.keys(COLORS).map(name => ({name, itemStyle: {color: COLORS[name]}}))
  const nodes = data.nodes.map((n: GraphNode) => ({
    name: n.id,
    symbolSize: n.center ? 36 : 24,
    category: n.category || '其他',
    itemStyle: n.center ? {borderColor: '#ec4899', borderWidth: 2, borderType: 'solid' as const} : {},
    label: {show: true, fontSize: 13, fontWeight: n.center ? 'bold' as const : 'normal' as const},
  }))
  const visibleEdges = data.edges.filter(edgesVisible)
  const links = visibleEdges.map((e: GraphEdge) => ({
    source: e.from,
    target: e.to,
    label: {
      show: true,
      formatter: e.label === RELATION_TYPES.PREREQUISITE ? '← 前置' : e.label === RELATION_TYPES.RELATED ? '相关' : e.label,
      fontSize: 10,
      color: '#64748b',
    },
    lineStyle: {
      color: e.label === RELATION_TYPES.PREREQUISITE ? '#7c3aed' : '#cbd5e1',
      width: e.label === RELATION_TYPES.PREREQUISITE ? 2.5 : 1.5,
      type: e.label === RELATION_TYPES.RELATED ? 'dashed' as const : 'solid' as const,
    },
  }))

  graphOption.value = {
    tooltip: {
      trigger: 'item' as const,
      formatter: (params: { name: string }) => {
        const node = data.nodes.find(n => n.id === params.name)
        return node ? `<b>${node.id}</b><br/>${node.category || '其他'}` : params.name
      },
    },
    legend: {
      data: categories.map(c => c.name),
      top: 8,
      textStyle: {fontSize: 11, color: '#64748b'},
    },
    series: [{
      type: 'graph',
      layout: 'force',
      categories,
      data: nodes,
      links,
      roam: true,
      draggable: true,
      force: {repulsion: 350, gravity: 0.08, edgeLength: [100, 250]},
      label: {show: true, position: 'right' as const, fontSize: 12, color: '#334155'},
      emphasis: {focus: 'adjacency' as const, lineStyle: {width: 4}},
      lineStyle: {curveness: 0.2, opacity: 0.7},
    }],
  }
}

function refilterGraph() {
  if (graphData.value) buildOption(graphData.value)
}

function onChartClick(params: { dataType?: string; name?: string }) {
  if (params.dataType !== 'node' || !params.name) return
  chartLoading.value = true
  api.get<GraphPayload>(`/api/graph/concepts/${encodeURIComponent(params.name)}/graph`)
    .then((json) => {
      chartLoading.value = false
      if (json.code !== 200 || !json.data || !json.data.nodes.length) return

      // merge new nodes/edges into existing data
      const existing = graphData.value
      if (!existing) return
      const existIds = new Set(existing.nodes.map(n => n.id))
      const existEdges = new Set(existing.edges.map(e => `${e.from}|||${e.to}|||${e.label}`))
      const newNodes = (json.data.nodes || []).filter(n => !existIds.has(n.id))
      const newEdges = (json.data.edges || []).filter(e => !existEdges.has(`${e.from}|||${e.to}|||${e.label}`))
      newEdges.forEach(e => existEdges.add(`${e.from}|||${e.to}|||${e.label}`))
      if (newNodes.length || newEdges.length) {
        existing.nodes.push(...newNodes)
        existing.edges.push(...newEdges)
        graphData.value = {...existing}
        buildOption(graphData.value)
      }
    })
}

async function toggleGraphView() {
  state.graphViewMode = !state.graphViewMode
  if (!state.graphViewMode) return
  if (!state.graphKeyword.trim()) {
    state.graphViewMode = false
    return showToast('请先输入概念名称', 'error')
  }
  chartLoading.value = true
  const json = await api.get<GraphPayload>(`/api/graph/concepts/${encodeURIComponent(state.graphKeyword.trim())}/graph`)
  chartLoading.value = false
  if (json.code !== 200 || !json.data || !json.data.nodes.length) {
    state.graphViewMode = false
    return showToast('未找到', 'error')
  }
  graphData.value = json.data
  buildOption(json.data)
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
          <option>中间件</option><option>编程语言</option><option>数据库</option><option>算法</option>
          <option>网络</option><option>操作系统</option><option>架构</option><option>前端</option>
          <option>安全</option><option>其他</option>
        </select>
        <select v-model="newDiff" class="kbase-search-input">
          <option :value="1">入门</option><option :value="2">基础</option><option :value="3">中级</option>
          <option :value="4">进阶</option><option :value="5">专家</option>
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
    </div>
    <div v-if="state.graphViewMode" class="graph-net">
      <div v-if="chartLoading" class="graph-loading">加载中…</div>
      <v-chart :option="graphOption" class="graph-chart" @click="onChartClick"/>
    </div>

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
      <button :disabled="state.graphPage<=1" class="kbase-search-btn" type="button" @click="state.graphPage--;search()">上一页</button>
      <span class="graph-page-num">{{ state.graphPage }}</span>
      <button class="kbase-search-btn" type="button" @click="state.graphPage++;search()">下一页</button>
    </div>

    <!-- Detail modal -->
    <Teleport to="body">
      <div v-if="detail" class="graph-modal-overlay" @click.self="detail=null">
        <div class="graph-modal">
          <button class="graph-modal-close" type="button" @click="detail=null"><CloseIcon :size="12"/></button>
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
.graph-cat-select { max-width: 140px }
.graph-btn-accent { background: var(--accent); color: #fff }
.graph-btn-view { margin-left: auto }

/* ---- Add form ---- */
.graph-add-form { background: #fff; border-radius: 12px; padding: 20px; margin-bottom: 16px; }
.graph-add-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.graph-add-actions { margin-top: 14px; display: flex; gap: 8px; justify-content: flex-end; }

/* ---- Graph network ---- */
.graph-net {
  width: 100%; height: 450px; border-radius: 12px; margin-bottom: 16px;
  background: #fafafa; border: 1px solid var(--border); position: relative;
}
.graph-chart { width: 100%; height: 100%; }
.graph-loading {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%);
  z-index: 10; font-size: 13px; color: var(--text-tertiary);
}

/* ---- Concept card ---- */
.graph-concept-card {
  padding: 12px 16px; background: #fff; border-radius: 10px;
  margin-bottom: 6px; cursor: pointer; border: 1px solid var(--border);
}
.graph-concept-name { font-weight: 700 }
.graph-concept-rel { font-size: 12px; color: var(--text-secondary); float: right }
.graph-concept-desc { font-size: 12px; color: var(--text-tertiary); margin-top: 4px }

/* ---- Pager ---- */
.graph-pager { display: flex; gap: 8px; justify-content: center; padding: 12px 0; }
.graph-page-num { line-height: 32px; font-size: 13px; color: var(--text-secondary) }

/* ---- Detail modal ---- */
.graph-detail-desc { font-size: 14px; color: var(--text-secondary); margin-bottom: 8px }
.graph-chip-row { display: flex; flex-wrap: wrap; gap: 6px }
.graph-doc-item { font-size: 13px; color: var(--text-secondary); display: flex; align-items: center; gap: 6px; }

/* ---- Filter bar ---- */
.graph-filter-bar { display: flex; gap: 6px; align-items: center; padding: 8px 0; margin-bottom: 4px; }
.graph-filter-btn {
  padding: 5px 14px; border-radius: 20px; font-size: 12px; font-weight: 600;
  border: 1.5px solid var(--border); background: var(--bg-surface);
  color: var(--text-tertiary); cursor: pointer; transition: all .2s;
}
.graph-filter-btn:hover { border-color: var(--accent-light); color: var(--accent) }
.graph-filter-btn.active { background: var(--accent); color: #fff; border-color: var(--accent); }

/* ---- Concept delete ---- */
.graph-concept-del {
  width: 26px; height: 26px; display: flex; align-items: center; justify-content: center;
  border-radius: 6px; color: var(--text-tertiary); cursor: pointer;
  transition: all .15s; flex-shrink: 0; margin-left: auto; margin-right: 8px;
}
.graph-concept-del:hover { background: #FEE2E2; color: #EF4444 }
.graph-name-input { grid-column: 1 / -1 }
</style>
