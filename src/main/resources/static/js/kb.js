// Knowledge Base
// =========================================================================

var K = {
  docs: [],
  searching: false
};

function showKnowledgeBase() {
  var html =
    '<div class="kbase-panel">' +
      // Toolbar: search + upload
      '<div class="kbase-toolbar">' +
        '<input class="kbase-search-input" id="kbaseSearchInput" type="text" ' +
          'placeholder="输入问题搜索知识库… (Enter 搜索)" ' +
          'onkeydown="if(event.key===\'Enter\')searchKnowledge()">' +
        '<button class="kbase-search-btn" onclick="searchKnowledge()">' + I.search + ' 搜索</button>' +
        '<button class="kbase-upload-btn" onclick="document.getElementById(\'kbaseFileInput\').click()">' +
          I.upload + ' 上传文档</button>' +
        '<input type="file" id="kbaseFileInput" style="display:none" multiple ' +
          'accept=".pdf,.md,.txt,.docx" onchange="uploadDocuments(this.files)">' +
      '</div>' +
      // Search result area
      '<div id="kbaseSearchResult" style="display:none">' +
        '<div class="kbase-search-answer" id="kbaseAnswer"></div>' +
        '<div class="kbase-sources" id="kbaseSources">' +
          '<div class="kbase-sources-label">' + I.book + ' 参考来源</div>' +
          '<div id="kbaseSourcesList"></div>' +
        '</div>' +
      '</div>' +
      // Document list
      '<div class="doc-list" id="docList"></div>' +
    '</div>';

  dom.chatBody.innerHTML = html;
  loadDocuments();
}

/** Fetch and render document list */
function loadDocuments() {
  var list = document.getElementById('docList');
  if (!list) return;
  list.innerHTML = '<div class="kbase-loading"><span class="spinner"></span>加载中…</div>';

  fetch('/api/knowledge/documents?page=1&size=50', authHeaders())
    .then(function(r) { return r.json(); })
    .then(function(json) {
      if (json.code !== 200) { list.innerHTML = '<div class="kbase-empty"><div class="kbase-empty-icon">❌</div><div class="kbase-empty-text">加载失败</div></div>'; return; }
      K.docs = json.data.records || [];
      renderDocumentList();
    })
    .catch(function() {
      list.innerHTML = '<div class="kbase-empty"><div class="kbase-empty-icon">❌</div><div class="kbase-empty-text">网络错误</div></div>';
    });
}

function renderDocumentList() {
  var list = document.getElementById('docList');
  if (!list) return;

  if (K.docs.length === 0) {
    list.innerHTML =
      '<div class="kbase-empty">' +
        '<div class="kbase-empty-icon">' + I.file + '</div>' +
        '<div class="kbase-empty-text">还没有上传文档</div>' +
        '<span style="font-size:12px;color:var(--text-tertiary)">点击上传按钮添加 PDF/MD/TXT/DOCX</span>' +
      '</div>';
    return;
  }

  var html = '';
  for (var i = 0; i < K.docs.length; i++) {
    var doc = K.docs[i];
    var icon = {'pdf':'📕','md':'📝','txt':'📄','docx':'📘'}[doc.fileType] || '📎';
    var statusLabel = {0:'⏳ 待处理',1:'📋 已解析',2:'✅ 已向量化'}[doc.status] || '未知';
    var statusClass = {0:'pending',1:'parsed',2:'vectorized'}[doc.status] || 'pending';
    var sizeStr = doc.fileSize < 1024 ? doc.fileSize + 'B'
                : doc.fileSize < 1048576 ? (doc.fileSize / 1024).toFixed(1) + 'KB'
                : (doc.fileSize / 1048576).toFixed(1) + 'MB';

    html +=
      '<div class="doc-card">' +
        '<div class="doc-card-icon">' + icon + '</div>' +
        '<div class="doc-card-info">' +
          '<div class="doc-card-name" title="' + escAttr(doc.filename) + '">' + escHtml(doc.filename) + '</div>' +
          '<div class="doc-card-meta">' +
            '<span>' + sizeStr + '</span>' +
            '<span>' + (doc.chunkCount || 0) + ' 分片</span>' +
            '<span class="doc-status ' + statusClass + '">' + statusLabel + '</span>' +
          '</div>' +
        '</div>' +
        (doc.downloadUrl ?
          '<a class="doc-card-del" title="下载" href="' + escAttr(doc.downloadUrl) + '" download style="text-decoration:none">📥</a>' : '') +
        '<button class="doc-card-del" title="删除" onclick="deleteDocument(' + doc.id + ',\'' + escAttr(doc.filename) + '\')">🗑</button>' +
      '</div>';
  }
  list.innerHTML = html;
}

/** Upload one or more documents */
function uploadDocuments(files) {
  if (!files || files.length === 0) return;

  // Validate all files first
  for (var i = 0; i < files.length; i++) {
    var ext = files[i].name.split('.').pop().toLowerCase();
    if (['pdf','md','txt','docx'].indexOf(ext) === -1) {
      showToast('不支持的文件类型: ' + ext + ' (文件: ' + files[i].name + ')', 'error');
      document.getElementById('kbaseFileInput').value = '';
      return;
    }
    if (files[i].size > 20 * 1024 * 1024) {
      showToast('文件大小不能超过 20MB: ' + files[i].name, 'error');
      document.getElementById('kbaseFileInput').value = '';
      return;
    }
  }

  // Show uploading state
  var list = document.getElementById('docList');
  if (list) {
    list.innerHTML = '<div class="kbase-loading"><span class="spinner"></span>正在解析 ' + files.length + ' 个文档…</div>';
  }

  var form = new FormData();
  for (var i = 0; i < files.length; i++) {
    form.append('files', files[i]);
  }

  fetch('/api/knowledge/documents/batch', authHeaders({ method:'POST', body:form }))
    .then(function(r) { return r.json(); })
    .then(function(json) {
      if (json.code === 200) {
        var results = json.data || [];
        var ok = results.filter(function(r) { return r.status === 1 || r.status === 2; }).length;
        var fail = results.filter(function(r) { return r.status === 0; }).length;
        if (fail > 0) {
          showToast('上传完成: ' + ok + ' 成功, ' + fail + ' 失败', 'error');
        } else {
          showToast('上传完成: ' + ok + ' 个文档', 'ok');
        }
        loadDocuments();
        var sr = document.getElementById('kbaseSearchResult');
        if (sr) sr.style.display = 'none';
      } else {
        showToast(json.message || '上传失败', 'error');
        loadDocuments();
      }
    })
    .catch(function() {
      showToast('上传失败', 'error');
      loadDocuments();
    });

  document.getElementById('kbaseFileInput').value = '';
}

/** Delete document */
function deleteDocument(id, filename) {
  if (!confirm('确定删除「' + filename + '」吗？\n\n该操作将同时删除所有分片和向量数据，不可恢复。')) return;

  fetch('/api/knowledge/documents/' + id, authHeaders({ method:'DELETE' }))
    .then(function(r) { return r.json(); })
    .then(function(json) {
      if (json.code === 200) {
        showToast('已删除: ' + filename, 'ok');
        K.docs = K.docs.filter(function(d) { return d.id !== id; });
        renderDocumentList();
        // Reset search
        var sr = document.getElementById('kbaseSearchResult');
        if (sr) sr.style.display = 'none';
      } else {
        showToast(json.message || '删除失败', 'error');
      }
    })
    .catch(function() {
      showToast('删除失败', 'error');
    });
}

/** Semantic search */
function searchKnowledge() {
  var input = document.getElementById('kbaseSearchInput');
  if (!input) return;
  var query = input.value.trim();
  if (!query) { showToast('请输入搜索内容', 'error'); return; }
  if (K.searching) return;

  K.searching = true;
  var answerEl = document.getElementById('kbaseAnswer');
  var resultWrap = document.getElementById('kbaseSearchResult');
  if (answerEl) answerEl.innerHTML = '<div class="kbase-loading"><span class="spinner"></span>正在检索知识库…</div>';
  if (resultWrap) resultWrap.style.display = '';

  fetch('/api/knowledge/search', authHeaders({
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({ query:query, topK:5 })
  }))
    .then(function(r) { return r.json(); })
    .then(function(json) {
      K.searching = false;
      if (json.code !== 200) {
        if (answerEl) answerEl.textContent = '❌ 检索失败: ' + (json.message || '未知错误');
        return;
      }
      renderSearchResult(json.data);
    })
    .catch(function() {
      K.searching = false;
      if (answerEl) answerEl.textContent = '❌ 网络错误，请重试';
    });
}

function renderSearchResult(data) {
  var answerEl = document.getElementById('kbaseAnswer');
  var sourcesList = document.getElementById('kbaseSourcesList');
  var sourcesWrap = document.getElementById('kbaseSources');

  // Render answer (plain text — could be markdown but keep simple for now)
  if (answerEl) {
    answerEl.innerHTML = data.answer
      ? data.answer.replace(/\n/g, '<br>')
      : '<span style="color:var(--text-tertiary)">知识库中未找到相关内容</span>';
  }

  // Render sources
  if (sourcesList && data.sources && data.sources.length > 0) {
    var html = '';
    for (var i = 0; i < data.sources.length; i++) {
      var s = data.sources[i];
      html +=
        '<div class="source-card">' +
          '<div class="source-card-header">' +
            '<span>' + escHtml(s.filename || '未知文档') + '</span>' +
            '<span style="color:var(--text-tertiary);font-size:11px">分片 #' + (s.chunkIndex != null ? s.chunkIndex + 1 : '?') + '</span>' +
            '<span class="source-card-score">' + (s.score * 100).toFixed(0) + '%</span>' +
          '</div>' +
          '<div>' + escHtml((s.content || '').substring(0, 300)) + (s.content && s.content.length > 300 ? '…' : '') + '</div>' +
        '</div>';
    }
    sourcesList.innerHTML = html;
    if (sourcesWrap) sourcesWrap.style.display = '';
  } else if (sourcesWrap) {
    sourcesWrap.style.display = 'none';
  }
}

// =========================================================================
