/**
 * Jiang I-Agent — Frontend Application
 */

if (!TOKEN || !USER) {
  location.href = '/login.html';
}

// =========================================================================
// State
// =========================================================================
const S = {
  conversationId: null,
  messages: [],
  streaming: false,
  activeTab: 'chat',
  convos: [],
  thinking: false,  // 思考模式开关
};

// =========================================================================
// DOM refs — lazy getters avoid stale refs after innerHTML replace
// =========================================================================
const $ = (sel, ctx) => (ctx||document).querySelector(sel);
const $$ = (sel, ctx) => [...(ctx||document).querySelectorAll(sel)];

const dom = {
  get chatBody()   { return $('#chatBody') },
  get sidebarList(){ return $('#sidebarList') },
  get msgInput()   { return $('#msgInput') },
  get sendBtn()    { return $('#sendBtn') },
  get welcome()    { return $('#welcome') },
  get inputArea()  { return $('#inputArea') },
  get toastWrap()  { return $('#toastWrap') },
};

// =========================================================================
// Tab switching
// =========================================================================
function switchTab(name) {
  S.activeTab = name;
  $$('.tab').forEach(function(t) {
    t.classList.toggle('active', t.dataset.tab === name);
  });

  if (name === 'chat') {
    dom.chatBody.innerHTML = '';
    dom.inputArea.style.display = '';
    if (S.conversationId) renderMessages(S.messages);
    else showWelcome();
  } else if (name === 'knowledge') {
    dom.inputArea.style.display = 'none';
    showKnowledgeBase();
  } else if (name === 'graph') {
    dom.inputArea.style.display = 'none';
    showPlaceholder('🕸️', '知识图谱',
      '概念关联、知识链查询、双检索融合。',
      'Phase 3 — 规划中');
  } else if (name === 'tools') {
    dom.inputArea.style.display = 'none';
    showToolsPanel();
  }
}

function showPlaceholder(icon, title, desc, badge) {
  dom.chatBody.innerHTML =
    '<div class="placeholder">' +
      '<div class="placeholder-icon">' + icon + '</div>' +
      '<h3>' + escHtml(title) + '</h3>' +
      '<p>' + escHtml(desc) + '</p>' +
      '<span class="placeholder-badge">' + escHtml(badge) + '</span>' +
    '</div>';
}

// =========================================================================
// Welcome screen
// =========================================================================
function showWelcome() {
  dom.chatBody.innerHTML =
    '<div class="welcome" id="welcome">' +
      '<div class="welcome-emoji">🧠</div>' +
      '<h2>Jiang I-Agent</h2>' +
      '<p>你的个人 AI 知识库助手。支持文档问答、代码答疑、知识图谱检索，具备工具调用与待办管理能力。</p>' +
      '<div class="welcome-cards">' +
        '<button class="welcome-card" onclick="sendHint(\'帮我记一下明天下午3点面试\')">📋 新建待办</button>' +
        '<button class="welcome-card" onclick="sendHint(\'用Java写一个快速排序\')">💻 代码问答</button>' +
        '<button class="welcome-card" onclick="sendHint(\'解释一下 RAG 检索增强生成的原理\')">📚 知识检索</button>' +
        '<button class="welcome-card" onclick="sendHint(\'什么是Java中的CompletableFuture\')">☕ 八股答疑</button>' +
      '</div>' +
    '</div>';
}

function sendHint(text) {
  if (S.activeTab !== 'chat') switchTab('chat');
  dom.msgInput.value = text;
  onSend();
}

// =========================================================================
// Sidebar — conversation list
// =========================================================================
function loadConversations() {
  fetch('/api/conversations?page=1&size=50', authHeaders())
    .then(function(res) { return res.json() })
    .then(function(json) {
      if (json.code !== 200) throw new Error(json.message);
      S.convos = json.data ? json.data.records || [] : [];
      renderSidebar();
    })
    .catch(function(e) {
      console.warn('加载会话列表失败:', e);
      dom.sidebarList.innerHTML = '<div class="sidebar-empty">会话列表加载失败</div>';
    });
}

function renderSidebar() {
  if (!S.convos.length) {
    dom.sidebarList.innerHTML = '<div class="sidebar-empty">暂无对话</div>';
    return;
  }
  dom.sidebarList.innerHTML = S.convos.map(function(c) {
    var activeClass = String(c.id) === S.conversationId ? ' active' : '';
    return '<div class="sidebar-convo' + activeClass + '"' +
      ' onclick="onSelectConvo(\'' + c.id + '\')"' +
      ' title="' + escAttr(c.title || '新对话') + '">' +
      '<span style="font-size:14px">💬</span>' +
      '<span class="text">' + escHtml(c.title || '新对话') + '</span>' +
      '<span class="del" onclick="event.stopPropagation();onDeleteConvo(\'' + c.id + '\')" title="删除">✕</span>' +
    '</div>';
  }).join('');
}

function onSelectConvo(id) {
  // 断开流式连接，停止打字机，防止串消息
  if (window._activeES) { window._activeES.close(); window._activeES = null; }
  if (window._typeTimer) { clearInterval(window._typeTimer); window._typeTimer = null; }
  S.streaming = false;
  dom.sendBtn.disabled = false;
  dom.msgInput.disabled = false;
  dom.msgInput.placeholder = '输入消息…';

  S.conversationId = String(id);
  renderSidebar();

  fetch('/api/conversations/' + id + '/messages?page=1&size=200', authHeaders())
    .then(function(res) { return res.json() })
    .then(function(json) {
      if (json.code === 200 && json.data && json.data.records) {
        S.messages = json.data.records.map(function(m) {
          return { role:m.role, content:m.content, toolCalls:m.toolCalls, createdAt:m.createdAt };
        });
        renderMessages(S.messages);
      }
    })
    .catch(function(e) {
      showToast('加载消息失败: ' + e.message, 'error');
    });
}

function onDeleteConvo(id) {
  if (!confirm('确定删除该会话及其所有消息？')) return;
  fetch('/api/conversations/' + id, authHeaders({ method:'DELETE' }))
    .then(function(res) { return res.json() })
    .then(function(json) {
      if (json.code === 200) {
        showToast('会话已删除', 'ok');
        if (S.conversationId === String(id)) onNewChat();
        loadConversations();
      } else {
        showToast(json.message || '删除失败', 'error');
      }
    })
    .catch(function(e) {
      showToast('删除失败: ' + e.message, 'error');
    });
}

function onNewChat() {
  if (window._activeES) { window._activeES.close(); window._activeES = null; }
  if (window._typeTimer) { clearInterval(window._typeTimer); window._typeTimer = null; }
  S.streaming = false;
  dom.sendBtn.disabled = false;
  dom.msgInput.disabled = false;
  dom.msgInput.placeholder = '输入消息…';

  S.conversationId = null;
  S.messages = [];
  renderSidebar();
  if (S.activeTab !== 'chat') switchTab('chat');
  else showWelcome();
  dom.msgInput.value = '';
  dom.msgInput.disabled = false;
  dom.msgInput.focus();
}

// =========================================================================
// Messages rendering
// =========================================================================
function renderMessages(msgs) {
  dom.chatBody.innerHTML = msgs.map(function(m, idx) {
    return renderMsg(m, idx);
  }).join('');
  scrollDown();
}

function avatarImg(url, isUser) {
  if (url) return '<img class="msg-avatar-img" src="' + escAttr(url) + '" alt="">';
  return '<div class="msg-avatar-fallback">' + (isUser ? '👤' : '🤖') + '</div>';
}

function renderMsg(m, idx) {
  var isUser = m.role === 'user';
  var url = isUser ? (USER && USER.avatar ? USER.avatar : '') : AGENT_AVATAR;
  var label = isUser ? 'You' : AGENT_NAME;
  var content = m.content || '';

  var toolTags = '';
  if (m.toolCalls && m.toolCalls.length) {
    toolTags = m.toolCalls.map(function(t) {
      return '<span class="tool-tag">⚙ ' + escHtml(t.name || 'tool') + '</span>';
    }).join('');
  }

  return '<div class="msg ' + (isUser ? 'user' : 'assistant') + '">' +
    avatarImg(url, isUser) +
    '<div class="msg-body">' +
      '<div class="msg-label">' + label + '</div>' +
      toolTags +
      '<div class="msg-bubble">' + renderMarkdown(content) + '</div>' +
    '</div>' +
  '</div>';
}

function scrollDown() {
  requestAnimationFrame(function() {
    dom.chatBody.scrollTop = dom.chatBody.scrollHeight;
  });
}

// =========================================================================
// Thinking mode toggle
// =========================================================================
function toggleThinking() {
  S.thinking = !S.thinking;
  var btn = $('#thinkBtn');
  var foot = $('#inputFootnote');
  if (S.thinking) {
    btn.classList.add('active');
    foot.textContent = '思考模式 · DeepSeek 展示推理过程';
  } else {
    btn.classList.remove('active');
    foot.textContent = 'Jiang I-Agent · 响应可能调用工具';
  }
}

// =========================================================================
// Send message & SSE streaming (EventSource + GET)
// =========================================================================
function onSend() {
  var text = dom.msgInput.value.trim();
  if (!text || S.streaming) return;

  dom.msgInput.value = '';
  dom.msgInput.style.height = 'auto';

  var w = $('#welcome');
  if (w) w.remove();

  appendMsg('user', text);
  S.messages.push({ role:'user', content:text });

  // 清理上一条消息遗留的 streaming 元素 id，避免串到旧气泡
  var oldBubble = document.getElementById('streamBubble');
  if (oldBubble) oldBubble.removeAttribute('id');
  var oldThinking = document.getElementById('thinkingBlock');
  if (oldThinking) oldThinking.removeAttribute('id');

  var isThinking = S.thinking;
  appendStreamingMsg(isThinking);

  S.streaming = true;
  dom.sendBtn.disabled = true;
  dom.msgInput.disabled = true;
  dom.msgInput.placeholder = isThinking ? 'AI 正在思考…' : 'AI 正在生成…';

  var fullContent = '';
  var fullThinking = '';
  var currentConvoId = S.conversationId;
  var displayedIdx = 0;
  var displayedThinkingIdx = 0;
  window._typeTimer = null;
  var TYPE_MS = 22;

  var url = '/api/chat/stream?message=' + encodeURIComponent(text)
    + '&token=' + encodeURIComponent(TOKEN);
  if (S.conversationId) url += '&conversationId=' + encodeURIComponent(S.conversationId);
  if (isThinking) url += '&thinking=true';

  if (window._activeES) { window._activeES.close(); }
  var es = new EventSource(url);
  window._activeES = es;

  /** 打字机（思考 + 正文共用定时器） */
  function pumpType() {
    // 先打思考内容
    if (displayedThinkingIdx < fullThinking.length) {
      var backlog = fullThinking.length - displayedThinkingIdx;
      var step = backlog > 200 ? 12 : backlog > 80 ? 6 : backlog > 30 ? 2 : 1;
      displayedThinkingIdx = Math.min(displayedThinkingIdx + step, fullThinking.length);
      var tBody = document.getElementById('thinkingBody');
      if (tBody) {
        tBody.textContent = fullThinking.substring(0, displayedThinkingIdx);
        tBody.scrollTop = tBody.scrollHeight;
      }
      scrollDown();
      return;
    }
    // 思考打完，打正文（比思考快一档）
    if (displayedIdx < fullContent.length) {
      var backlogC = fullContent.length - displayedIdx;
      var stepC = backlogC > 200 ? 16 : backlogC > 80 ? 9 : backlogC > 30 ? 4 : 2;
      displayedIdx = Math.min(displayedIdx + stepC, fullContent.length);
      var el = document.getElementById('streamBubble');
      if (el) {
        el.innerHTML = escHtml(fullContent.substring(0, displayedIdx)).replace(/\n/g, '<br>') + '<span class="cursor"></span>';
      }
      scrollDown();
    } else if (!S.streaming) {
      finalizeAnswer();
    }
  }

  function finalizeAnswer() {
    clearInterval(window._typeTimer);
    window._typeTimer = null;

    var bubble = document.getElementById('streamBubble');
    if (bubble && fullContent) {
      bubble.innerHTML = renderMarkdown(fullContent);
      bubble.removeAttribute('id');
    }

    var saveContent = fullThinking
      ? '<thinking>' + fullThinking + '</thinking>\n' + fullContent
      : fullContent;
    S.messages.push({ role: 'assistant', content: saveContent });

    if (!S.conversationId) {
      setTimeout(function() {
        loadConversations();
        if (S.convos.length && !S.conversationId) {
          S.conversationId = String(S.convos[0].id);
          renderSidebar();
        }
      }, 600);
    }
  }

  function ensureTyping() {
    if (!window._typeTimer) {
      window._typeTimer = setInterval(pumpType, TYPE_MS);
    }
  }

  es.onmessage = function(e) {
    var data = e.data;

    // 尝试 JSON 解析（思考模式 + 工具调用都是 JSON 事件）
    try {
      var evt = JSON.parse(data);
      if (evt.type === 'thinking') {
        fullThinking += evt.content;
        var tBlock = document.getElementById('thinkingBlock');
        if (tBlock) tBlock.style.display = '';
        ensureTyping();
        return;
      } else if (evt.type === 'content') {
        fullContent += evt.content;
        ensureTyping();
        return;
      } else if (evt.type === 'tool_call') {
        // 工具调用指示器
        var tcNote = '\n\n🔧 调用工具: ' + escHtml(evt.name) + '\n';
        fullContent += tcNote;
        ensureTyping();
        return;
      }
      // 未知 JSON 类型，忽略
      return;
    } catch (ex) {
      // 非 JSON：兼容旧的纯文本模式
      fullContent += data;
      ensureTyping();
    }
  };

  es.onerror = function() {
    es.close();
    window._activeES = null;
    S.streaming = false;

    dom.sendBtn.disabled = false;
    dom.msgInput.disabled = false;
    dom.msgInput.placeholder = '输入消息…';
    dom.msgInput.focus();

    // 清理 cursor
    var bubble = document.getElementById('streamBubble');
    if (bubble) {
      var cursor = bubble.querySelector('.cursor');
      if (cursor) cursor.remove();
    }

    if (!fullContent && !fullThinking) {
      fullContent = '（AI 未响应，请确认后端服务已启动且 API Key 有效）';
    }

    if (S.conversationId !== currentConvoId) {
      return;
    }

    // 就地更新已有的 thinkingBlock（appendStreamingMsg 创建的），不新建
    var tBlock = document.getElementById('thinkingBlock');
    if (tBlock && fullThinking) {
      tBlock.style.display = '';
      var tHeader = tBlock.querySelector('.thinking-header');
      var tBody = tBlock.querySelector('.thinking-body');
      if (tHeader) {
        tHeader.innerHTML = '🧠 思考过程 (' + (fullThinking.length > 100 ? (fullThinking.length + ' 字') : '简短') + ') <span style="font-size:10px;margin-left:auto;color:var(--text-tertiary)">点击折叠</span>';
        tHeader.onclick = function() {
          var b = tBlock.querySelector('.thinking-body');
          if (b) b.style.display = b.style.display === 'none' ? '' : 'none';
        };
      }
      if (tBody) tBody.textContent = fullThinking;
      tBlock.removeAttribute('id');
    } else if (tBlock) {
      tBlock.remove();
    }

    // 打字机会自己检测 S.streaming == false 并调用 finalizeAnswer()
  };
}

// =========================================================================
// DOM helpers for messages
// =========================================================================
function appendMsg(role, content) {
  var isUser = role === 'user';
  var url = isUser ? (USER && USER.avatar ? USER.avatar : '') : AGENT_AVATAR;
  var label = isUser ? 'You' : AGENT_NAME;
  var div = document.createElement('div');
  div.className = 'msg ' + (isUser ? 'user' : 'assistant');
  div.innerHTML =
    avatarImg(url, isUser) +
    '<div class="msg-body">' +
      '<div class="msg-label">' + label + '</div>' +
      '<div class="msg-bubble">' + renderMarkdown(content) + '</div>' +
    '</div>';
  dom.chatBody.appendChild(div);
  scrollDown();
}

function appendStreamingMsg(withThinking) {
  var div = document.createElement('div');
  div.className = 'msg assistant';
  var thinkingHTML = '';
  if (withThinking) {
    thinkingHTML =
      '<div class="thinking-block" id="thinkingBlock" style="display:none">' +
        '<div class="thinking-header">🧠 思考中…</div>' +
        '<div class="thinking-body" id="thinkingBody"></div>' +
      '</div>';
  }
  div.innerHTML =
    avatarImg(AGENT_AVATAR, false) +
    '<div class="msg-body">' +
      '<div class="msg-label">' + AGENT_NAME + '</div>' +
      thinkingHTML +
      '<div class="msg-bubble" id="streamBubble">' + (withThinking ? '' : '<span class="cursor"></span>') + '</div>' +
    '</div>';
  dom.chatBody.appendChild(div);
  scrollDown();
  return document.getElementById('streamBubble');
}

// =========================================================================
// Keyboard helpers
// =========================================================================
function onInputKey(e) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); onSend(); }
}

function autoGrow(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 130) + 'px';
}

// =========================================================================
// Lightweight Markdown renderer
// =========================================================================
function renderMarkdown(text) {
  if (!text) return '';

  // 提取 <thinking> 块
  var thinkingContent = '';
  var displayText = text;
  var thinkMatch = text.match(/^<thinking>([\s\S]*?)<\/thinking>\n?/);
  if (thinkMatch) {
    thinkingContent = thinkMatch[1];
    displayText = text.substring(thinkMatch[0].length);
  }

  var thinkingHTML = '';
  if (thinkingContent) {
    thinkingHTML =
      '<div class="thinking-block">' +
        '<div class="thinking-header" onclick="var b=this.nextElementSibling;b.style.display=b.style.display===\'none\'?\'\':\'none\'">🧠 思考过程 <span style="font-size:10px;margin-left:auto;color:var(--text-tertiary)">点击折叠</span></div>' +
        '<div class="thinking-body">' + escHtml(thinkingContent) + '</div>' +
      '</div>';
  }

  var s = escHtml(displayText);

  // code blocks
  s = s.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre><code>$2</code></pre>');
  // inline code
  s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
  // bold
  s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  // italic
  s = s.replace(/\*(.+?)\*/g, '<em>$1</em>');
  // headings (allow variable whitespace after #, e.g. "###标题" or "###  标题")
  s = s.replace(/^###\s+(.+)/gm, '<h3>$1</h3>');
  s = s.replace(/^##\s+(.+)/gm, '<h2>$1</h2>');
  s = s.replace(/^#\s+(.+)/gm, '<h1>$1</h1>');
  // blockquote
  s = s.replace(/^&gt; (.+)/gm, '<blockquote>$1</blockquote>');
  // horizontal rule
  s = s.replace(/^---$/gm, '<hr>');
  // unordered list
  s = s.replace(/^[*-] (.+)/gm, '<li>$1</li>');
  // ordered list
  s = s.replace(/^\d+\. (.+)/gm, '<li>$1</li>');
  // wrap consecutive <li> in <ul>
  s = s.replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul>$1</ul>');
  // paragraphs & line breaks
  s = s.replace(/\n\n/g, '<br><br>');
  s = s.replace(/\n/g, '<br>');

  return thinkingHTML + s;
}

// =========================================================================
// Bootstrap
// =========================================================================
/** 全局缓存的 Agent 头像 */
var AGENT_AVATAR = '';
var AGENT_NAME = 'Jiang I-Agent';

function loadAgentProfile() {
  fetch('/api/admin/agent/profile')
    .then(function(r) { return r.json(); })
    .then(function(json) {
      if (json.code === 200 && json.data) {
        AGENT_AVATAR = json.data.avatar || '';
        AGENT_NAME = json.data.agentName || 'Jiang I-Agent';
      }
    });
}

document.addEventListener('DOMContentLoaded', function() {
  loadAgentProfile();
  if (USER) {
    document.getElementById('sidebarNickname').textContent = USER.nickname || USER.username;
    var av = document.getElementById('sidebarAvatar');
    if (USER.avatar) { av.src = USER.avatar; } else { av.style.display = 'none'; }
    if (USER.role === 'ADMIN') {
      document.getElementById('sidebarAdminLink').style.display = '';
      document.getElementById('sidebarRoleBadge').style.display = '';
    }
  }
  loadConversations();
  dom.msgInput.focus();
});

// =========================================================================
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
        '<button class="kbase-search-btn" onclick="searchKnowledge()">搜索</button>' +
        '<button class="kbase-upload-btn" onclick="document.getElementById(\'kbaseFileInput\').click()">' +
          '📄 上传文档</button>' +
        '<input type="file" id="kbaseFileInput" style="display:none" multiple ' +
          'accept=".pdf,.md,.txt,.docx" onchange="uploadDocuments(this.files)">' +
      '</div>' +
      // Search result area
      '<div id="kbaseSearchResult" style="display:none">' +
        '<div class="kbase-search-answer" id="kbaseAnswer"></div>' +
        '<div class="kbase-sources" id="kbaseSources">' +
          '<div class="kbase-sources-label">📖 参考来源</div>' +
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
        '<div class="kbase-empty-icon">📂</div>' +
        '<div class="kbase-empty-text">还没有上传文档</div>' +
        '<span style="font-size:12px;color:var(--text-tertiary)">点击"上传文档"添加 PDF/MD/TXT/DOCX</span>' +
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
          '<div style="font-size:13px;font-weight:700;color:var(--text-secondary);padding:4px 0">📋 未完成</div>' +
          '<div id="todoPendingList" class="doc-list" style="flex:1"></div>' +
        '</div>' +
        // Done
        '<div style="flex:1;display:flex;flex-direction:column;gap:8px;overflow-y:auto">' +
          '<div style="font-size:13px;font-weight:700;color:var(--text-secondary);padding:4px 0">✅ 已完成</div>' +
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
      var html = '<div style="font-size:13px;font-weight:700;color:var(--text-secondary);margin-bottom:8px">🔧 可用工具 (' + tools.length + ')</div>';
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
