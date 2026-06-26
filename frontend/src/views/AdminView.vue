<script setup>
import { ref, onMounted } from 'vue'
import { TOKEN } from '../stores/state'

const tab = ref('users')
const users = ref([])
const agent = ref({ agentName:'', avatar:'', model:'', temperature:0.7, systemPrompt:'' })
const toast = ref('')

function show(t) { toast.value = t; setTimeout(() => toast.value='', 2000) }
const h = (url, opts) => { opts = opts || {}; opts.headers = { ...opts.headers, 'Authorization':'Bearer '+TOKEN }; return fetch(url, opts).then(r => r.json()) }

async function loadUsers() {
  const json = await h('/api/admin/users?page=1&size=100')
  if (json.code === 200) users.value = json.data?.records || []
}

async function deleteUser(id, name) {
  if (!confirm(`确定删除用户 "${name}"？`)) return
  const json = await h('/api/admin/users/'+id, { method:'DELETE' })
  if (json.code === 200) { show('已删除'); loadUsers() } else show(json.message)
}

async function loadAgent() {
  const json = await h('/api/admin/agent')
  if (json.code === 200) agent.value = json.data
}

async function uploadAgentAvatar(e) {
  const file = e.target.files[0]; if (!file) return
  const form = new FormData(); form.append('file', file)
  const r = await fetch('/api/profile/avatar', { method:'POST', headers:{'Authorization':'Bearer '+TOKEN}, body:form })
  const json = await r.json()
  if (json.code === 200 && json.data?.url) { agent.value.avatar = json.data.url; show('头像已更新，别忘了保存') } else show(json.message||'上传失败')
}

async function saveAgent() {
  const json = await h('/api/admin/agent', { method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify(agent.value) })
  if (json.code === 200) { show('配置已更新') } else { show(json.message) }
}

onMounted(() => { loadUsers(); loadAgent() })
</script>

<template>
<div class="admin-page">
  <div v-if="toast" class="toast ok">{{ toast }}</div>
  <div class="card">
    <div class="tabs">
      <button :class="{ active: tab==='users' }" @click="tab='users'">用户管理</button>
      <button :class="{ active: tab==='agent' }" @click="tab='agent'">Agent 配置</button>
    </div>
    <div v-if="tab==='users'">
      <h3>用户列表</h3>
      <table><thead><tr><th>ID</th><th>用户名</th><th>昵称</th><th>角色</th><th>注册时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="u in users" :key="u.id">
          <td>{{ u.id }}</td><td>{{ u.username }}</td><td>{{ u.nickname }}</td>
          <td><span :class="['badge', u.role==='ADMIN'?'admin':'user']">{{ u.role }}</span></td>
          <td>{{ (u.createdAt||'').substring(0,10) }}</td>
          <td><button class="btn-sm" @click="deleteUser(u.id, u.username)">删除</button></td>
        </tr>
      </tbody></table>
    </div>
    <div v-if="tab==='agent'">
      <h3>Agent 全局配置</h3>
      <div class="avatar-area">
        <img class="avatar-preview" :src="agent.avatar||''" alt="">
        <div><input type="file" accept="image/*" style="display:none" ref="agentFile" @change="uploadAgentAvatar"><button class="btn-outline" @click="$refs.agentFile.click()" style="font-size:13px;padding:8px 16px">更换头像</button></div>
      </div>
      <div class="row">
        <div class="field"><label>Agent 名称</label><input v-model="agent.agentName"></div>
        <div class="field"><label>模型</label><input v-model="agent.model"></div>
        <div class="field"><label>温度 (0-2)</label><input v-model.number="agent.temperature" type="number" step="0.1" min="0" max="2"></div>
      </div>
      <div class="field"><label>系统提示词</label><textarea v-model="agent.systemPrompt"></textarea></div>
      <button class="btn" @click="saveAgent">保存配置</button>
    </div>
  </div>
</div>
</template>

<style scoped>
.admin-page { max-width:800px;margin:40px auto;padding:0 20px }
.card { background:var(--bg-surface,#fff);border-radius:12px;padding:28px 32px;box-shadow:0 4px 24px rgba(30,41,59,.08) }
.card h3 { font-size:16px;font-weight:700;margin-bottom:18px }
.tabs { display:flex;gap:0;border-bottom:1px solid var(--border,#F0E2EF);margin-bottom:20px }
.tabs button { padding:10px 20px;font-size:13px;font-weight:600;color:var(--text-secondary,#64748B);background:none;border:none;cursor:pointer;border-bottom:2px solid transparent }
.tabs button.active { color:var(--accent,#EC4899);border-bottom-color:var(--accent,#EC4899) }
table { width:100%;border-collapse:collapse }
th,td { text-align:left;padding:10px 12px;font-size:13px;border-bottom:1px solid var(--border,#F0E2EF) }
th { font-weight:600;color:var(--text-secondary,#64748B) }
.badge { display:inline-block;padding:2px 8px;border-radius:20px;font-size:11px;font-weight:700 }
.badge.admin { background:rgba(236,72,153,.1);color:#EC4899 }
.badge.user { background:rgba(139,92,246,.08);color:#8B5CF6 }
.btn-sm { padding:8px 18px;border-radius:20px;font-size:13px;font-weight:600;cursor:pointer;border:1px solid var(--border,#F0E2EF);background:#fff;color:#EF4444 }
.btn-sm:hover { background:#EF4444;color:#fff;border-color:#EF4444 }
.btn { padding:10px 24px;border:none;border-radius:8px;background:linear-gradient(135deg,#EC4899,#F472B6);color:#fff;font-size:14px;font-weight:700;cursor:pointer }
.btn-outline { background:transparent;color:#EC4899;border:1.5px solid #EC4899;border-radius:8px;font-weight:600;cursor:pointer }
.field { margin-bottom:16px }
.field label { display:block;font-size:13px;font-weight:600;margin-bottom:5px;color:var(--text-secondary,#64748B) }
.field input,.field textarea { width:100%;padding:10px 14px;border:1.5px solid var(--border,#F0E2EF);border-radius:8px;font-size:14px;outline:none }
.field textarea { min-height:200px;resize:vertical }
.row { display:flex;gap:16px }
.row .field { flex:1 }
.avatar-area { display:flex;align-items:center;gap:16px;margin-bottom:20px }
.avatar-preview { width:64px;height:64px;border-radius:50%;object-fit:cover;border:3px solid var(--border,#F0E2EF) }
.toast { position:fixed;top:16px;right:16px;padding:10px 18px;border-radius:8px;font-size:13px;font-weight:600;z-index:999;background:#22C55E;color:#fff }
</style>
