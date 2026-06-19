/**
 * Jiang I-Agent — 公共工具函数
 */
var TOKEN = localStorage.getItem('token');
var USER  = JSON.parse(localStorage.getItem('user') || 'null');

/** 自动给 fetch options 加 Authorization */
function authHeaders(opts) {
  opts = opts || {};
  opts.headers = opts.headers || {};
  opts.headers['Authorization'] = 'Bearer ' + TOKEN;
  return opts;
}

/** 退出登录 */
function logout() {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  location.href = '/login.html';
}

/** Toast 提示 */
function showToast(msg, type) {
  type = type || 'info';
  var t = document.createElement('div');
  t.className = 'toast ' + type;
  t.textContent = msg;
  var wrap = document.getElementById('toastWrap') || document.body;
  wrap.appendChild(t);
  setTimeout(function() {
    t.style.opacity = '0';
    t.style.transition = 'opacity .2s';
    setTimeout(function() { t.remove(); }, 220);
  }, 2800);
}

/** HTML 转义 */
function escHtml(str) {
  var m = {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'};
  return String(str).replace(/[&<>"']/g, function(c){ return m[c]; });
}
function escAttr(str) {
  return String(str).replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
