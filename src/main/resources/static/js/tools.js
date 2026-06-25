// Tools & Todo Panel
// =========================================================================

function showToolsPanel() {
  var html =
    '<div class="kbase-panel">' +
      // Todo Input
      '<div class="kbase-toolbar">' +
        '<input class="kbase-search-input" id="todoInput" type="text" ' +
          'placeholder="添加待办… (Enter 创建)" ' +
          'onkeydown="if(event.key===\'Enter\')createTodoItem()">' +
        '<button class="kbase-search-btn" onclick="createTodoItem()">添加</button>' +
      '</div>' +
      // Todo List
      '<div style="display:flex;gap:16px;flex:1;min-height:0">' +
        // Pending
        '<div style="flex:1;display:flex;flex-direction:column;gap:8px;overflow-y:auto">' +
          '<div style="font-size:13px;font-weight:700;color:var(--text-secondary);padding:4px 0;display:flex;align-items:center;gap:6px">' + I.edit + ' 未完成</div>' +
          '<div id="todoPendingList" class="doc-list" style="flex:1"></div>' +
        '</div>' +
        // Done
        '<div style="flex:1;display:flex;flex-direction:column;gap:8px;overflow-y:auto">' +
          '<div style="font-size:13px;font-weight:700;color:var(--text-secondary);padding:4px 0;display:flex;align-items:center;gap:6px">' + I.check + ' 已完成</div>' +
          '<div id="todoDoneList" class="doc-list" style="flex:1"></div>' +
        '</div>' +
      '</div>' +
      // Available Tools
      '<div id="toolsList" style="margin-top:4px"></div>' +
    '</div>';

  dom.chatBody.innerHTML = html;
  loadTodoList();
  loadTools();
}

function loadTodoList() {
  fetch('/api/todos', authHeaders())
    .then(function(r) { return r.json(); })
    .then(function(json) {
      if (json.code !== 200) return;
      var todos = json.data || [];
      var pending = todos.filter(function(t) { return t.isDone === 0; });
      var done = todos.filter(function(t) { return t.isDone === 1; });

      var pHtml = '';
      for (var i = 0; i < pending.length; i++) {
        pHtml += renderTodoCard(pending[i]);
      }
      if (pending.length === 0) {
        pHtml = '<div style="padding:28px;text-align:center;color:var(--text-tertiary);font-size:13px">暂无待办</div>';
      }
      document.getElementById('todoPendingList').innerHTML = pHtml;

      var dHtml = '';
      for (var i = 0; i < done.length; i++) {
        dHtml += renderTodoCard(done[i]);
      }
      if (done.length === 0) {
        dHtml = '<div style="padding:28px;text-align:center;color:var(--text-tertiary);font-size:13px">暂无已完成</div>';
      }
      document.getElementById('todoDoneList').innerHTML = dHtml;
    });
}

function renderTodoCard(item) {
  var dueHtml = item.dueDate
    ? '<span style="font-size:11px;color:var(--text-tertiary)">截止: ' + item.dueDate + '</span>'
    : '';
  var doneClass = item.isDone === 1 ? 'todo-done' : '';
  return '<div class="doc-card ' + doneClass + '" style="padding:10px 14px">' +
    (item.isDone === 1
      ? '<button class="doc-card-del" title="还原" onclick="toggleTodoItem(' + item.id + ',false)" style="font-size:14px">↩</button>'
      : '<button class="doc-card-del" title="完成" onclick="toggleTodoItem(' + item.id + ',true)" style="font-size:14px">✓</button>' ) +
    '<div class="doc-card-info" style="flex:1">' +
      '<div class="doc-card-name" style="font-size:14px">' + escHtml(item.title) + '</div>' +
      '<div class="doc-card-meta">' +
        '<span>' + new Date(item.createdAt).toLocaleDateString('zh-CN') + '</span>' +
        dueHtml +
      '</div>' +
    '</div>' +
    '<button class="doc-card-del" title="删除" onclick="deleteTodoItem(' + item.id + ')">🗑</button>' +
  '</div>';
}

function createTodoItem() {
  var input = document.getElementById('todoInput');
  if (!input) return;
  var title = input.value.trim();
  if (!title) return;

  fetch('/api/todos', authHeaders({
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({ title:title })
  }))
    .then(function(r) { return r.json(); })
    .then(function(json) {
      if (json.code === 200) {
        input.value = '';
        input.placeholder = '已添加: ' + title;
        setTimeout(function() { input.placeholder = '添加待办… (Enter 创建)'; }, 2000);
        loadTodoList();
      } else {
        showToast('添加失败: ' + (json.message || ''), 'error');
      }
    });
}

function toggleTodoItem(id, done) {
  var url = '/api/todos/' + id + (done ? '/complete' : '/uncomplete');
  // uncomplete not implemented, use complete endpoint
  fetch('/api/todos/' + id + '/complete', authHeaders({ method:'PUT' }))
    .then(function(r) { return r.json(); })
    .then(function() { loadTodoList(); });
}

function deleteTodoItem(id) {
  fetch('/api/todos/' + id, authHeaders({ method:'DELETE' }))
    .then(function(r) { return r.json(); })
    .then(function() { loadTodoList(); });
}

function loadTools() {
  fetch('/api/tools', authHeaders())
    .then(function(r) { return r.json(); })
    .then(function(json) {
      if (json.code !== 200) return;
      var tools = json.data || [];
      var el = document.getElementById('toolsList');
      if (!el || tools.length === 0) return;
      var html = '<div style="font-size:13px;font-weight:700;color:var(--text-secondary);margin-bottom:8px;display:flex;align-items:center;gap:6px">' + I.tool + ' 可用工具 (' + tools.length + ')</div>';
      for (var i = 0; i < tools.length; i++) {
        html += '<div class="doc-card" style="padding:10px 14px;margin-bottom:6px">' +
          '<div class="doc-card-icon" style="font-size:16px">🔧</div>' +
          '<div class="doc-card-info">' +
            '<div style="font-size:14px;font-weight:700">' + escHtml(tools[i].name) + '</div>' +
            '<div style="font-size:12px;color:var(--text-tertiary)">' + escHtml(tools[i].description) + '</div>' +
          '</div>' +
        '</div>';
      }
      el.innerHTML = html;
    });
}
