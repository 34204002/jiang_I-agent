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
    showPlaceholder('📚', '知识库',
      '文档上传、向量检索、RAG 增强问答。',
      'Phase 2 — 开发中');
  } else if (name === 'graph') {
    dom.inputArea.style.display = 'none';
    showPlaceholder('🕸️', '知识图谱',
      '概念关联、知识链查询、双检索融合。',
      'Phase 3 — 规划中');
  } else if (name === 'tools') {
    dom.inputArea.style.display = 'none';
    showPlaceholder('🔧', '工具调用',
      'Agent 注册工具：待办、代码执行、接口调试等。',
      'Phase 4 — 规划中');
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

    // 思考模式：解析 JSON 事件
    if (isThinking) {
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
        }
        // 忽略其他类型（如 done）
        return;
      } catch (ex) {
        // 非 JSON，按普通文本处理（兼容）
      }
    }

    // 普通模式：直接拼接文本
    fullContent += data;
    ensureTyping();
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
