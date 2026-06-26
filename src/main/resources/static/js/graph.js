// Graph Panel — 知识图谱
// =========================================================================

function showGraphPanel() {
  var html =
    '<div class="kbase-panel" id="graphPanelRoot">' +
      // Toolbar: search + add button
      '<div class="kbase-toolbar">' +
        '<input class="kbase-search-input" id="graphSearch" type="text" ' +
          'placeholder="搜索概念… (Enter)" ' +
          'onkeydown="if(event.key===\'Enter\')graphSearch()">' +
        '<select class="kbase-search-input" id="graphCatFilter" style="max-width:140px">' +
          '<option value="">全部分类</option>' +
        '</select>' +
        '<button class="kbase-search-btn" onclick="graphSearch()">搜索</button>' +
        '<button class="kbase-search-btn" onclick="toggleAddConcept()" style="background:var(--accent);color:#fff">' + I.plus + ' 添加</button>' +
        '<button class="kbase-search-btn" id="graphViewToggle" onclick="toggleGraphView()" style="margin-left:auto">🕸️ 图视图</button>' +
      '</div>' +

      // Add concept form (hidden by default)
      '<div id="addConceptForm" style="display:none;background:#fff;border-radius:12px;padding:20px;margin-bottom:16px">' +
        '<div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">' +
          '<input id="addConceptName" placeholder="概念名称 *" style="grid-column:1/-1" class="kbase-search-input">' +
          '<input id="addConceptDesc" placeholder="描述" class="kbase-search-input">' +
          '<select id="addConceptCat" class="kbase-search-input">' +
            '<option value="">选择分类</option>' +
            '<option value="中间件">中间件</option>' +
            '<option value="编程语言">编程语言</option>' +
            '<option value="数据库">数据库</option>' +
            '<option value="算法">算法</option>' +
            '<option value="网络">网络</option>' +
            '<option value="操作系统">操作系统</option>' +
            '<option value="架构">架构</option>' +
            '<option value="前端">前端</option>' +
            '<option value="安全">安全</option>' +
            '<option value="其他">其他</option>' +
          '</select>' +
          '<select id="addConceptDiff" class="kbase-search-input">' +
            '<option value="1">⭐ 入门</option>' +
            '<option value="2">⭐⭐ 基础</option>' +
            '<option value="3" selected>⭐⭐⭐ 中级</option>' +
            '<option value="4">⭐⭐⭐⭐ 进阶</option>' +
            '<option value="5">⭐⭐⭐⭐⭐ 专家</option>' +
          '</select>' +
        '</div>' +
        '<div style="margin-top:14px;display:flex;gap:8px;justify-content:flex-end">' +
          '<button class="kbase-search-btn" onclick="toggleAddConcept()">取消</button>' +
          '<button class="kbase-search-btn" style="background:var(--accent);color:#fff" onclick="doAddConcept()">保存</button>' +
        '</div>' +
      '</div>' +

      // Path finder
      '<div id="pathFinder" style="display:flex;gap:10px;align-items:center;margin-bottom:16px;padding:12px 16px;background:#fff;border-radius:12px;flex-wrap:wrap">' +
        '<span style="font-size:13px;font-weight:600;white-space:nowrap">学习路径:</span>' +
        '<input class="kbase-search-input" id="pathFrom" placeholder="起始概念" style="max-width:150px">' +
        '<span style="font-size:13px;color:var(--text-secondary)">→</span>' +
        '<input class="kbase-search-input" id="pathTo" placeholder="目标概念" style="max-width:150px">' +
        '<button class="kbase-search-btn" onclick="findPath()">查询</button>' +
        '<button style="display:none" id="pathExtractBtn" class="kbase-search-btn" onclick="extractFromDoc()">从文档提取</button>' +
      '</div>' +

      // Graph visualization container
      '<div id="graphNetContainer" style="display:none;width:100%;height:450px;border-radius:12px;margin-bottom:16px;background:#fafafa;border:1px solid var(--border)"></div>' +

      // Path result
      '<div id="pathResult" style="margin-bottom:16px"></div>' +

      // Concept list
      '<div id="graphConceptList" class="doc-list" style="flex:1;overflow-y:auto"></div>' +

      // Pagination
      '<div id="graphPager" style="display:flex;gap:8px;justify-content:center;padding:12px 0"></div>' +
    '</div>';

  dom.chatBody.innerHTML = html;
  graphPage = 1;
  graphKeyword = '';
  graphCategory = '';
  graphSearch();
  loadCategories();
}

var graphPage = 1;
var graphKeyword = '';
var graphCategory = '';

// ==================== Search & Render ====================

function graphSearch() {
  var kw = $('#graphSearch') ? $('#graphSearch').value.trim() : '';
  var cat = $('#graphCatFilter') ? $('#graphCatFilter').value : '';
  graphKeyword = kw;
  graphCategory = cat;

  var params = '?page=' + graphPage + '&size=20';
  if (kw) params += '&keyword=' + encodeURIComponent(kw);
  if (cat) params += '&category=' + encodeURIComponent(cat);

  fetch('/api/graph/concepts' + params)
    .then(function(r) { return r.json(); })
    .then(function(res) {
      if (res.code !== 200) { showToast(res.message); return; }
      var data = res.data;
      renderConceptList(data.records, data.total);
      renderGraphPager(data.total);
    })
    .catch(function(e) {
      showToast('概念搜索失败: ' + e.message);
    });
}

function renderConceptList(records, total) {
  var el = $('#graphConceptList');
  if (!el) return;
  if (!records || records.length === 0) {
    el.innerHTML = '<div class="placeholder"><div class="placeholder-icon">🕸️</div>' +
      '<p style="margin-top:12px;font-size:15px;font-weight:600;color:var(--text-primary)">知识图谱还是空的</p>' +
      '<p style="font-size:13px;color:var(--text-secondary)">手动添加概念，或跟 Agent 对话让它帮你记录</p></div>';
    return;
  }

  var html = '';
  records.forEach(function(c) {
    var stars = '';
    for (var i = 0; i < 5; i++) {
      stars += i < c.difficulty ? '★' : '☆';
    }
    html +=
      '<div class="doc-card graph-concept-card" onclick="showConceptDetail(\'' + escHtml(c.name) + '\')">' +
        '<div class="doc-card-icon">📘</div>' +
        '<div class="doc-card-body">' +
          '<div class="doc-card-title">' + escHtml(c.name) +
            '<span class="graph-cat-tag">' + escHtml(c.category || '未分类') + '</span>' +
          '</div>' +
          '<div class="doc-card-meta">' +
            '<span class="graph-stars">' + stars + '</span>' +
            '<span> · ' + c.relationCount + ' 个关联</span>' +
          '</div>' +
          '<div class="doc-card-desc">' + escHtml(c.description || '') + '</div>' +
        '</div>' +
      '</div>';
  });
  el.innerHTML = html;
}

function renderGraphPager(total) {
  var el = $('#graphPager');
  if (!el) return;
  var pages = Math.ceil(total / 20);
  if (pages <= 1) { el.innerHTML = ''; return; }
  var h = '';
  for (var i = 1; i <= Math.min(pages, 10); i++) {
    h += '<button class="kbase-search-btn' + (i === graphPage ? ' graph-page-active' : '') +
      '" onclick="graphPage=' + i + ';graphSearch()">' + i + '</button>';
  }
  el.innerHTML = h;
}

// ==================== Concept Detail Modal ====================

function showConceptDetail(name) {
  fetch('/api/graph/concepts/' + encodeURIComponent(name))
    .then(function(r) { return r.json(); })
    .then(function(res) {
      if (res.code !== 200) { showToast(res.message); return; }
      var c = res.data;
      var stars = '';
      for (var i = 0; i < 5; i++) stars += i < c.difficulty ? '★' : '☆';

      var preqHtml = '';
      if (c.prerequisites && c.prerequisites.length > 0) {
        preqHtml = '<div class="graph-section"><div class="graph-section-title">前置知识</div>';
        c.prerequisites.forEach(function(p) {
          preqHtml += '<span class="graph-ref-chip" onclick="event.stopPropagation();showConceptDetail(\'' + escHtml(p.name) + '\')">' +
            escHtml(p.name) + ' (' + p.relation + ')</span>';
        });
        preqHtml += '</div>';
      }

      var relHtml = '';
      if (c.related && c.related.length > 0) {
        relHtml = '<div class="graph-section"><div class="graph-section-title">相关概念</div>';
        c.related.forEach(function(r) {
          relHtml += '<span class="graph-ref-chip" style="background:var(--accent-light);color:var(--accent)" onclick="event.stopPropagation();showConceptDetail(\'' + escHtml(r.name) + '\')">' +
            escHtml(r.name) + '</span>';
        });
        relHtml += '</div>';
      }

      var docHtml = '';
      if (c.documents && c.documents.length > 0) {
        docHtml = '<div class="graph-section"><div class="graph-section-title">关联文档</div>';
        c.documents.forEach(function(d) {
          docHtml += '<span class="graph-ref-chip" style="background:var(--bg-tertiary);color:var(--text-secondary)">📄 ' + escHtml(d.filename) + '</span>';
        });
        docHtml += '</div>';
      }

      var modalHtml =
        '<div class="graph-modal-overlay" onclick="closeGraphDetail(event)">' +
          '<div class="graph-modal" onclick="event.stopPropagation()">' +
            '<button class="graph-modal-close" onclick="closeGraphDetail()">&times;</button>' +
            '<div class="graph-modal-header">' +
              '<div class="graph-modal-icon">📘</div>' +
              '<div>' +
                '<h2>' + escHtml(c.name) + '</h2>' +
                '<div style="display:flex;gap:8px;align-items:center;margin-top:4px">' +
                  '<span class="graph-cat-tag">' + escHtml(c.category || '未分类') + '</span>' +
                  '<span class="graph-stars">' + stars + '</span>' +
                '</div>' +
              '</div>' +
            '</div>' +
            '<div class="graph-modal-body">' +
              '<p style="color:var(--text-primary);line-height:1.7">' + escHtml(c.description || '暂无描述') + '</p>' +
              preqHtml + relHtml + docHtml +
            '</div>' +
          '</div>' +
        '</div>';

      var overlay = document.createElement('div');
      overlay.innerHTML = modalHtml;
      document.body.appendChild(overlay.firstElementChild);
    })
    .catch(function(e) {
      showToast('获取概念详情失败: ' + e.message);
    });
}

function closeGraphDetail(e) {
  if (e && e.target && !e.target.classList.contains('graph-modal-overlay')) return;
  var ol = document.querySelector('.graph-modal-overlay');
  if (ol) ol.remove();
}

// ==================== Path Finder ====================

function findPath() {
  var from = ($('#pathFrom') || {}).value || '';
  var to = ($('#pathTo') || {}).value || '';
  if (!from || !to) { showToast('请输入起始和目标概念'); return; }

  fetch('/api/graph/concepts/' + encodeURIComponent(from) + '/path?target=' + encodeURIComponent(to) + '&maxHops=5')
    .then(function(r) { return r.json(); })
    .then(function(res) {
      if (res.code !== 200) { showToast(res.message); return; }
      var data = res.data;
      var el = $('#pathResult');
      if (!el) return;
      if (!data.paths || data.paths.length === 0) {
        el.innerHTML = '<div style="padding:12px 16px;background:var(--bg-tertiary);border-radius:12px;font-size:13px">' +
          '未找到从「' + escHtml(from) + '」到「' + escHtml(to) + '」的学习路径。</div>';
        return;
      }
      var h = '';
      data.paths.forEach(function(path, i) {
        h += '<div class="graph-path-chain">' +
          '<span style="font-weight:600;color:var(--accent);margin-right:8px">路径 ' + (i + 1) + '</span>';
        path.forEach(function(n, j) {
          if (j > 0) h += '<span class="graph-path-arrow">→</span>';
          h += '<span class="graph-path-node">' + escHtml(n.name) + ' ⭐' + n.difficulty + '</span>';
        });
        h += '</div>';
      });
      el.innerHTML = h;
    });
}

// ==================== Add Concept ====================

function toggleAddConcept() {
  var form = $('#addConceptForm');
  if (!form) return;
  form.style.display = form.style.display === 'none' ? 'block' : 'none';
  if (form.style.display === 'block') {
    var nameEl = $('#addConceptName');
    if (nameEl) { nameEl.value = ''; nameEl.focus(); }
    var descEl = $('#addConceptDesc');
    if (descEl) descEl.value = '';
  }
}

function doAddConcept() {
  var name = ($('#addConceptName') || {}).value || '';
  var desc = ($('#addConceptDesc') || {}).value || '';
  var cat = ($('#addConceptCat') || {}).value || '';
  var diff = parseInt(($('#addConceptDiff') || {}).value || '3');

  if (!name.trim()) { showToast('概念名称不能为空'); return; }

  fetch('/api/graph/concepts', authHeaders({
    method: 'POST',
    body: JSON.stringify({ name: name.trim(), description: desc.trim(), category: cat, difficulty: diff })
  }))
    .then(function(r) { return r.json(); })
    .then(function(res) {
      if (res.code !== 200) { showToast(res.message); return; }
      showToast('概念「' + name + '」已添加');
      toggleAddConcept();
      graphSearch();
    });
}

// ==================== Extract from Document ====================

function extractFromDoc(docId) {
  if (!docId) {
    // Prompt for document ID
    var input = prompt('输入要提取概念的文档 ID');
    if (!input) return;
    docId = parseInt(input);
    if (isNaN(docId)) { showToast('无效的文档 ID'); return; }
  }

  fetch('/api/graph/extract', authHeaders({
    method: 'POST',
    body: JSON.stringify({ documentId: docId })
  }))
    .then(function(r) { return r.json(); })
    .then(function(res) {
      if (res.code !== 200) { showToast(res.message); return; }
      showToast(res.data.message);
      graphSearch();
    });
}

// ==================== Categories ====================

function loadCategories() {
  // Static categories — could be dynamic from API in the future
  var cats = ['中间件', '编程语言', '数据库', '算法', '网络', '操作系统', '架构', '前端', '安全', '其他'];
  var sel = $('#graphCatFilter');
  if (!sel) return;
  cats.forEach(function(c) {
    var opt = document.createElement('option');
    opt.value = c;
    opt.textContent = c;
    sel.appendChild(opt);
  });
}

// ==================== Graph Visualization (vis-network) ====================

var graphViewMode = false;
var graphNetwork = null;

function toggleGraphView() {
  if (typeof vis === 'undefined') { showToast('vis-network 库未加载，请刷新页面重试'); return; }
  graphViewMode = !graphViewMode;
  var btn = document.getElementById('graphViewToggle');
  var container = document.getElementById('graphNetContainer');
  var list = document.getElementById('graphConceptList');
  var pager = document.getElementById('graphPager');

  if (graphViewMode) {
    btn.textContent = '📋 列表';
    container.style.display = '';
    list.style.display = 'none';
    if (pager) pager.style.display = 'none';
    loadGraphView();
  } else {
    btn.textContent = '🕸️ 图视图';
    container.style.display = 'none';
    list.style.display = '';
    if (pager) pager.style.display = '';
    if (graphNetwork) { graphNetwork.destroy(); graphNetwork = null; }
  }
}

function loadGraphView(conceptName) {
  var name = conceptName || (document.getElementById('graphSearch') || {}).value || '';
  if (!name.trim()) { showToast('请输入要查看的概念名称'); toggleGraphView(); return; }

  fetch('/api/graph/concepts/' + encodeURIComponent(name.trim()) + '/graph')
    .then(function(r) { return r.json(); })
    .then(function(res) {
      if (res.code !== 200) { showToast(res.message || '加载失败'); return; }
      var data = res.data;
      if (!data.nodes || data.nodes.length === 0) {
        showToast('未找到概念: ' + name);
        return;
      }
      renderGraph(data);
    })
    .catch(function(e) { showToast('加载失败: ' + e.message); });
}

function renderGraph(data) {
  var container = document.getElementById('graphNetContainer');
  if (!container) return;

  var colors = {
    '中间件': '#f59e0b', '编程语言': '#3b82f6', '数据库': '#10b981',
    '算法': '#8b5cf6', '网络': '#ef4444', '操作系统': '#06b6d4',
    '架构': '#f97316', '前端': '#ec4899', '安全': '#dc2626', '其他': '#6b7280'
  };

  var nodes = new vis.DataSet(data.nodes.map(function(n) {
    return {
      id: n.id,
      label: n.id + ' ⭐' + n.difficulty,
      color: {
        background: n.center ? '#ec4899' : (colors[n.category] || '#6b7280'),
        border: n.center ? '#be185d' : '#555',
        highlight: { background: n.center ? '#f472b6' : '#999' }
      },
      font: { color: '#fff', size: 13, face: 'Inter' },
      borderWidth: n.center ? 3 : 1,
      shape: n.center ? 'star' : 'dot',
      size: n.center ? 35 : 20
    };
  }));

  var edges = new vis.DataSet(data.edges.map(function(e, i) {
    return {
      id: i,
      from: e.from,
      to: e.to,
      label: e.label === 'PREREQUISITE_OF' ? '前置' : e.label === 'RELATED_TO' ? '相关' : e.label,
      arrows: 'to',
      color: { color: e.label === 'PREREQUISITE_OF' ? '#7c3aed' : '#94a3b8' },
      font: { size: 10, color: '#64748b', strokeWidth: 0 }
    };
  }));

  if (graphNetwork) graphNetwork.destroy();

  var options = {
    physics: { solver: 'forceAtlas2Based', forceAtlas2Based: { gravitationalConstant: -40, centralGravity: 0.01, springLength: 150, springConstant: 0.08 } },
    interaction: { hover: true, tooltipDelay: 200, zoomView: true, dragView: true, navigationButtons: false },
    layout: { improvedLayout: true },
    nodes: { borderWidthSelected: 3, chosen: true }
  };

  graphNetwork = new vis.Network(container, { nodes: nodes, edges: edges }, options);

  // 双击节点加载其邻居
  graphNetwork.on('doubleClick', function(params) {
    if (params.nodes.length > 0) {
      var nodeId = params.nodes[0];
      fetch('/api/graph/concepts/' + encodeURIComponent(nodeId) + '/graph')
        .then(function(r) { return r.json(); })
        .then(function(res) {
          if (res.code !== 200) return;
          var nd = res.data;
          var exist = nodes.getIds();
          nd.nodes.forEach(function(n) {
            if (exist.indexOf(n.id) === -1) nodes.add({ id: n.id, label: n.id + ' ⭐' + n.difficulty,
              color: { background: (colors[n.category] || '#6b7280'), border: '#555',
                highlight: { background: '#999' } },
              font: { color: '#fff', size: 13 }, shape: 'dot', size: 20 });
          });
          nd.edges.forEach(function(e, i) {
            edges.add({ id: 'e_' + Date.now() + '_' + i, from: e.from, to: e.to,
              label: e.label === 'PREREQUISITE_OF' ? '前置' : '相关',
              arrows: 'to', color: { color: e.label === 'PREREQUISITE_OF' ? '#7c3aed' : '#94a3b8' },
              font: { size: 10, color: '#64748b' } });
          });
        });
    }
  });
}
