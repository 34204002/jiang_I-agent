<script setup>
import { ref, watch, onMounted, nextTick } from 'vue'
import { state } from '../stores/state'
import { api } from '../utils/api'
import { Network } from 'vis-network'
import { DataSet } from 'vis-data'

const concepts = ref([])
const adding = ref(false), newName = ref(''), newDesc = ref(''), newCat = ref(''), newDiff = ref(3)
const pathFrom = ref(''), pathTo = ref(''), pathResult = ref('')
let graphNetwork = null

const COLORS = { '中间件':'#f59e0b','编程语言':'#3b82f6','数据库':'#10b981','算法':'#8b5cf6','网络':'#ef4444','操作系统':'#06b6d4','架构':'#f97316','前端':'#ec4899','安全':'#dc2626','其他':'#6b7280' }

async function search() {
  const p = state.graphKeyword ? `keyword=${encodeURIComponent(state.graphKeyword)}&` : ''
  const c = state.graphCategory ? `category=${encodeURIComponent(state.graphCategory)}&` : ''
  const json = await api.get(`/api/graph/concepts?${p}${c}page=${state.graphPage}`)
  if (json.code===200 && json.data) concepts.value = json.data.records || []
}

async function doAdd() {
  if (!newName.value.trim()) return alert('概念名称不能为空')
  await api.post('/api/graph/concepts', { name:newName.value.trim(), description:newDesc.value, category:newCat.value||'其他', difficulty:newDiff.value })
  adding.value = false; newName.value = ''; search()
}

async function showDetail(name) {
  const json = await api.get(`/api/graph/concepts/${encodeURIComponent(name)}`)
  if (json.code!==200) return
  const d = json.data
  let h = `<div class="graph-modal-overlay" onclick="event.target===this&&this.remove()"><div class="graph-modal"><div class="graph-modal-close" onclick="this.closest('.graph-modal-overlay').remove()">✕</div>`
  h += `<h2>${d.name} <span class="graph-cat-tag">${d.category||''}</span> ⭐${d.difficulty||1}</h2>`
  h += `<p style="font-size:14px;color:var(--text-secondary)">${d.description||''}</p>`
  if (d.prerequisites?.length) h += `<div class="graph-section-title">前置知识</div><div style="display:flex;flex-wrap:wrap;gap:6px">${d.prerequisites.map(p=>`<span class="graph-ref-chip">${p.name} ⭐${p.difficulty}</span>`).join('')}</div>`
  if (d.related?.length) h += `<div class="graph-section-title">相关概念</div><div style="display:flex;flex-wrap:wrap;gap:6px">${d.related.map(r=>`<span class="graph-ref-chip">${r.name}</span>`).join('')}</div>`
  if (d.documents?.length) h += `<div class="graph-section-title">关联文档</div>${d.documents.map(dc=>`<div style="font-size:13px;color:var(--text-secondary)">📄 ${dc.filename}</div>`).join('')}`
  h += `</div></div>`
  const el = document.createElement('div'); el.innerHTML = h; document.body.appendChild(el.firstChild)
}

async function findPath() {
  if (!pathFrom.value||!pathTo.value) return alert('请输入起始和目标概念')
  const json = await api.get(`/api/graph/concepts/${encodeURIComponent(pathFrom.value)}/path?target=${encodeURIComponent(pathTo.value)}&maxHops=5`)
  if (json.code!==200) return
  if (!json.data.paths?.length) { pathResult.value = `<div style="padding:12px 16px;background:var(--bg-tertiary);border-radius:12px;font-size:13px">未找到路径。</div>`; return }
  pathResult.value = json.data.paths.map((p,i) => `<div class="graph-path-chain"><span style="font-weight:600;color:var(--accent);margin-right:8px">路径${i+1}</span>${p.map((n,j)=>(j>0?'<span class="graph-path-arrow">→</span>':'')+`<span class="graph-path-node">${n.name} ⭐${n.difficulty}</span>`).join('')}</div>`).join('')
}

async function toggleGraphView() {
  state.graphViewMode = !state.graphViewMode
  if (state.graphViewMode) {
    await nextTick()
    if (!state.graphKeyword.trim()) { state.graphViewMode = false; return alert('请先输入概念名称') }
    const json = await api.get(`/api/graph/concepts/${encodeURIComponent(state.graphKeyword.trim())}/graph`)
    if (json.code!==200||!json.data.nodes.length) { state.graphViewMode = false; return alert('未找到') }
    renderGraph(json.data)
  }
}

function renderGraph(data) {
  const el = document.getElementById('graphNetContainer')
  if (!el) return; if (graphNetwork) graphNetwork.destroy()
  const nodes = new DataSet(data.nodes.map(n => ({
    id:n.id, label:n.id, color:{ background:n.center?'#ec4899':(COLORS[n.category]||'#6b7280'), border:n.center?'#be185d':'#555' },
    font:{ color:'#fff', size:13 }, borderWidth:n.center?3:1, shape:n.center?'star':'dot', size:n.center?35:20
  })))
  const edges = new DataSet(data.edges.map((e,i) => ({
    id:i, from:e.from, to:e.to, label:e.label==='PREREQUISITE_OF'?'前置':e.label==='RELATED_TO'?'相关':e.label,
    arrows:'to', color:{ color:e.label==='PREREQUISITE_OF'?'#7c3aed':'#94a3b8' }, font:{ size:10, color:'#64748b' }
  })))
  graphNetwork = new Network(el, { nodes, edges }, {
    physics:{ solver:'forceAtlas2Based', forceAtlas2Based:{ gravitationalConstant:-40, centralGravity:0.01, springLength:150, springConstant:0.08 } },
    interaction:{ hover:true, zoomView:true, dragView:true }, layout:{ improvedLayout:true }
  })
  graphNetwork.on('doubleClick', p => { if (p.nodes.length) fetch(`/api/graph/concepts/${encodeURIComponent(p.nodes[0])}/graph`).then(r=>r.json()).then(res=>{ if(res.code!==200)return;(res.data.nodes||[]).forEach(n=>{if(nodes.getIds().indexOf(n.id)===-1)nodes.add({id:n.id,label:n.id,color:{background:(COLORS[n.category]||'#6b7280'),border:'#555'},font:{color:'#fff',size:13},shape:'dot',size:20})});(res.data.edges||[]).forEach(e=>edges.add({id:'e'+Date.now()+Math.random().toString(36).slice(2),from:e.from,to:e.to,label:e.label==='PREREQUISITE_OF'?'前置':'相关',arrows:'to',color:{color:e.label==='PREREQUISITE_OF'?'#7c3aed':'#94a3b8'},font:{size:10,color:'#64748b'}}))})})
}

onMounted(search)
</script>

<template>
<div class="kbase-panel">
  <div class="kbase-toolbar">
    <input class="kbase-search-input" v-model="state.graphKeyword" @keydown.enter="search" placeholder="搜索概念…">
    <select class="kbase-search-input" v-model="state.graphCategory" style="max-width:140px"><option value="">全部分类</option></select>
    <button class="kbase-search-btn" @click="search">搜索</button>
    <button class="kbase-search-btn" style="background:var(--accent);color:#fff" @click="adding=!adding">+ 添加</button>
    <button class="kbase-search-btn" @click="toggleGraphView" style="margin-left:auto">{{ state.graphViewMode ? '📋 列表' : '🕸️ 图视图' }}</button>
  </div>
  <div v-if="adding" style="background:#fff;border-radius:12px;padding:20px;margin-bottom:16px">
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
      <input v-model="newName" placeholder="概念名称 *" class="kbase-search-input" style="grid-column:1/-1">
      <input v-model="newDesc" placeholder="描述" class="kbase-search-input">
      <select v-model="newCat" class="kbase-search-input"><option value="">分类</option><option>中间件</option><option>编程语言</option><option>数据库</option><option>算法</option><option>网络</option><option>操作系统</option><option>架构</option><option>前端</option><option>安全</option><option>其他</option></select>
      <select v-model="newDiff" class="kbase-search-input"><option :value="1">⭐入门</option><option :value="2">⭐⭐基础</option><option :value="3">⭐⭐⭐中级</option><option :value="4">⭐⭐⭐⭐进阶</option><option :value="5">⭐⭐⭐⭐⭐专家</option></select>
    </div>
    <div style="margin-top:14px;display:flex;gap:8px;justify-content:flex-end"><button class="kbase-search-btn" @click="adding=false">取消</button><button class="kbase-search-btn" style="background:var(--accent);color:#fff" @click="doAdd">保存</button></div>
  </div>
  <div style="display:flex;gap:10px;align-items:center;margin-bottom:16px;padding:12px 16px;background:#fff;border-radius:12px;flex-wrap:wrap">
    <span style="font-size:13px;font-weight:600">学习路径:</span>
    <input class="kbase-search-input" v-model="pathFrom" placeholder="起始概念" style="max-width:150px">
    <span>→</span>
    <input class="kbase-search-input" v-model="pathTo" placeholder="目标概念" style="max-width:150px">
    <button class="kbase-search-btn" @click="findPath">查询</button>
  </div>
  <div v-if="pathResult" style="margin-bottom:16px" v-html="pathResult"></div>
  <div v-if="state.graphViewMode" id="graphNetContainer" style="width:100%;height:450px;border-radius:12px;margin-bottom:16px;background:#fafafa;border:1px solid var(--border)"></div>
  <div v-if="!state.graphViewMode" class="doc-list" style="flex:1;overflow-y:auto">
    <div v-for="c in concepts" :key="c.name" class="graph-concept-card" @click="showDetail(c.name)" style="padding:12px 16px;background:#fff;border-radius:10px;margin-bottom:6px;cursor:pointer;border:1px solid var(--border)">
      <span style="font-weight:700">{{ c.name }}</span><span class="graph-cat-tag">{{ c.category }}</span>
      <span style="font-size:12px;color:var(--text-secondary);float:right">{{ c.relationCount }} 关系</span>
      <div style="font-size:12px;color:var(--text-tertiary);margin-top:4px">{{ c.description }}</div>
    </div>
  </div>
  <div v-if="!state.graphViewMode" style="display:flex;gap:8px;justify-content:center;padding:12px 0">
    <button class="kbase-search-btn" :disabled="state.graphPage<=1" @click="state.graphPage--;search()">上一页</button>
    <span style="line-height:32px;font-size:13px;color:var(--text-secondary)">{{ state.graphPage }}</span>
    <button class="kbase-search-btn" @click="state.graphPage++;search()">下一页</button>
  </div>
</div>
</template>
