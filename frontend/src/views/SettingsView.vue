<script setup>
import { ref, onMounted } from 'vue'
import { TOKEN } from '../stores/state'

const username = ref(''), nickname = ref(''), role = ref(''), avatar = ref(''), toast = ref('')

function show(t, type) { toast.value = t; setTimeout(() => toast.value = '', 2000) }

async function load() {
  const r = await fetch('/api/user/me', { headers: { 'Authorization': 'Bearer ' + TOKEN } })
  const json = await r.json()
  if (json.code === 200) {
    const u = json.data
    username.value = u.username; nickname.value = u.nickname || ''; role.value = u.role; avatar.value = u.avatar || ''
    localStorage.setItem('user', JSON.stringify(u))
  }
}

const fileInput = ref(null)

async function uploadAvatar(e) {
  const file = e.target.files[0]; if (!file) return
  const form = new FormData(); form.append('file', file)
  const r = await fetch('/api/profile/avatar', { method:'POST', headers:{'Authorization':'Bearer '+TOKEN}, body:form })
  const json = await r.json()
  if (json.code === 200 && json.data?.url) {
    avatar.value = json.data.url
    await fetch('/api/user/me', { method:'PUT', headers:{'Authorization':'Bearer '+TOKEN,'Content-Type':'application/json'}, body:JSON.stringify({ avatar: json.data.url }) })
    show('头像已更新')
  } else show(json.message || '上传失败')
}

async function save() {
  if (!nickname.value.trim()) { show('昵称不能为空'); return }
  const r = await fetch('/api/user/me', { method:'PUT', headers:{'Authorization':'Bearer '+TOKEN,'Content-Type':'application/json'}, body:JSON.stringify({ nickname: nickname.value.trim() }) })
  const json = await r.json()
  if (json.code === 200) {
    localStorage.setItem('user', JSON.stringify(json.data))
    show('保存成功')
  } else show(json.message || '保存失败')
}

onMounted(load)
</script>

<template>
<div style="flex:1;overflow-y:auto">
<div class="settings-page">
  <div style="display:flex;align-items:center;gap:12px;margin-bottom:24px">
    <router-link to="/chat" style="font-size:13px;color:var(--accent);text-decoration:none;font-weight:600">← 返回对话</router-link>
    <span style="font-size:16px;font-weight:700">个人设置</span>
  </div>
  <div v-if="toast" class="toast ok">{{ toast }}</div>
  <div class="card"><h3>个人资料</h3>
    <div class="avatar-area">
      <img class="avatar-preview" :src="avatar||''" alt="头像">
      <div><input type="file" accept="image/jpeg,image/png,image/webp,image/gif" style="display:none" ref="fileInput" @change="uploadAvatar"><button class="btn-outline" @click="fileInput.click()" style="font-size:13px;padding:8px 16px">更换头像</button>
      <div style="font-size:11px;color:var(--text-secondary);margin-top:4px">JPG/PNG/WebP/GIF</div></div>
    </div>
    <div class="field"><label>用户名</label><input :value="username" disabled style="opacity:.5"></div>
    <div class="field"><label>昵称</label><input v-model="nickname" placeholder="给自己取个名字"></div>
    <div class="field"><label>角色</label><input :value="role" disabled style="opacity:.5"></div>
    <button class="btn" @click="save">保存</button>
  </div>
</div>
</div>
</template>

<style scoped>
.settings-page { max-width:560px;margin:40px auto;padding:0 20px }
.card { background:var(--bg-surface,#fff);border-radius:12px;padding:28px 32px;box-shadow:0 4px 24px rgba(30,41,59,.08);margin-bottom:20px }
.card h3 { font-size:16px;font-weight:700;margin-bottom:18px }
.field { margin-bottom:16px }
.field label { display:block;font-size:13px;font-weight:600;margin-bottom:5px;color:var(--text-secondary,#64748B) }
.field input { width:100%;padding:10px 14px;border:1.5px solid var(--border,#F0E2EF);border-radius:8px;font-size:14px;outline:none }
.avatar-area { display:flex;align-items:center;gap:16px;margin-bottom:16px }
.avatar-preview { width:72px;height:72px;border-radius:50%;object-fit:cover;border:3px solid var(--border,#F0E2EF);background:var(--bg-body,#FDF2F8) }
.btn { padding:10px 24px;border:none;border-radius:8px;background:linear-gradient(135deg,#EC4899,#F472B6);color:#fff;font-size:14px;font-weight:700;cursor:pointer }
.btn-outline { background:transparent;color:#EC4899;border:1.5px solid #EC4899;border-radius:8px;font-weight:600;cursor:pointer }
.toast { position:fixed;top:16px;right:16px;padding:10px 18px;border-radius:8px;font-size:13px;font-weight:600;z-index:999;background:#22C55E;color:#fff }
</style>
