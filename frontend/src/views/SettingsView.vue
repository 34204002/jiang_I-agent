<script lang="ts" setup>
import {onMounted, ref, useTemplateRef} from 'vue'
import {api} from '../utils/api'
import {showToast} from '../utils/toast'
import {writeUser} from '../utils/storage'
import {USER} from '../stores/state'
import type {UserInfo} from '../types'

const username = ref(''), nickname = ref(''), role = ref(''), avatar = ref('')
const llmModel = ref('')
const apiKeyMasked = ref('')
const newApiKey = ref('')

async function load() {
  const json = await api.get<UserInfo>('/api/user/me')
  if (json.code === 200 && json.data) {
    const u = json.data
    username.value = u.username || ''
    nickname.value = u.nickname || ''
    role.value = u.role || ''
    avatar.value = u.avatar || ''
    llmModel.value = u.llmModel || ''
    apiKeyMasked.value = u.apiKeyMasked || ''
    writeUser(u)
    Object.assign(USER, u)
  }
}

const fileInput = useTemplateRef<HTMLInputElement>('fileInput')

async function uploadAvatar(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0];
  if (!file) return
  const form = new FormData();
  form.append('file', file)
  const json = await api.postForm<{ url: string }>('/api/profile/avatar', form)
  if (json.code === 200 && json.data?.url) {
    avatar.value = json.data.url
    await api.put('/api/user/me', {avatar: json.data.url})
    showToast('头像已更新', 'ok')
  } else showToast(json.message || '上传失败', 'error')
}

async function save() {
  if (!nickname.value.trim()) {
    showToast('昵称不能为空', 'error')
    return
  }
  const json = await api.put<UserInfo>('/api/user/me', {nickname: nickname.value.trim()})
  if (json.code === 200) {
    writeUser(json.data)
    Object.assign(USER, json.data)
    showToast('保存成功', 'ok')
  } else showToast(json.message || '保存失败', 'error')
}

// ====== 修改密码 ======
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const changingPassword = ref(false)

async function changePassword() {
  if (!oldPassword.value) {
    showToast('请输入原密码', 'error')
    return
  }
  if (!newPassword.value || newPassword.value.length < 6) {
    showToast('新密码至少 6 位', 'error')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    showToast('两次输入的新密码不一致', 'error')
    return
  }
  changingPassword.value = true
  try {
    const json = await api.put('/api/profile/password', {
      oldPassword: oldPassword.value,
      newPassword: newPassword.value
    })
    if (json.code === 200) {
      showToast('密码已修改', 'ok')
      oldPassword.value = newPassword.value = confirmPassword.value = ''
    } else showToast(json.message || '修改失败', 'error')
  } finally {
    changingPassword.value = false
  }
}

// ====== 对话模型（BYOK：自带 DeepSeek Key） ======
async function saveLlmConfig() {
  const payload: Record<string, string> = { llmModel: llmModel.value.trim() }
  // 只在用户填了新 key 时才发送，避免误清已有的 key
  if (newApiKey.value.trim()) payload.apiKey = newApiKey.value.trim()
  const json = await api.put<UserInfo>('/api/user/me', payload)
  if (json.code === 200 && json.data) {
    llmModel.value = json.data.llmModel || ''
    apiKeyMasked.value = json.data.apiKeyMasked || ''
    newApiKey.value = ''
    showToast('对话模型配置已保存', 'ok')
  } else showToast(json.message || '保存失败', 'error')
}

async function clearApiKey() {
  const json = await api.put<UserInfo>('/api/user/me', {apiKey: ''})
  if (json.code === 200 && json.data) {
    apiKeyMasked.value = ''
    newApiKey.value = ''
    showToast('已清除，回退系统默认 key', 'ok')
  } else showToast(json.message || '清除失败', 'error')
}

onMounted(load)
</script>

<template>
  <div class="settings-shell">
    <div class="settings-page">
      <div class="settings-back-bar">
        <router-link class="settings-back-link" to="/chat">←
          返回对话
        </router-link>
        <span class="settings-title">个人设置</span>
      </div>
      <div class="card"><h3>个人资料</h3>
        <div class="avatar-area">
          <img :src="avatar||''" alt="头像" class="avatar-preview">
          <div><input ref="fileInput" accept="image/jpeg,image/png,image/webp,image/gif" class="settings-hidden-input"
                      type="file" @change="uploadAvatar">
            <button class="btn-outline settings-upload-btn" @click="fileInput?.click()">更换头像
            </button>
            <div class="settings-upload-hint">JPG/PNG/WebP/GIF</div>
          </div>
        </div>
        <div class="field"><label>用户名</label><input :value="username" class="settings-disabled" disabled></div>
        <div class="field"><label>昵称</label><input v-model="nickname" placeholder="给自己取个名字"></div>
        <div class="field"><label>角色</label><input :value="role" class="settings-disabled" disabled></div>
        <button class="btn" @click="save">保存</button>
      </div>
      <div class="card"><h3>修改密码</h3>
        <div class="field"><label>原密码</label><input v-model="oldPassword" placeholder="当前登录密码" type="password"></div>
        <div class="field"><label>新密码</label><input v-model="newPassword" placeholder="至少 6 位" type="password"></div>
        <div class="field"><label>确认新密码</label><input v-model="confirmPassword" placeholder="再次输入新密码" type="password"></div>
        <button class="btn" :disabled="changingPassword" @click="changePassword">
          {{ changingPassword ? '提交中...' : '修改密码' }}
        </button>
      </div>
      <div class="card"><h3>对话模型（可选 · 自带 Key）</h3>
        <div class="field">
          <label>模型</label>
          <input v-model="llmModel" list="deepseek-models" placeholder="留空 = 使用系统默认模型">
          <datalist id="deepseek-models">
            <option value="deepseek-v4-flash"></option>
            <option value="deepseek-chat"></option>
            <option value="deepseek-reasoner"></option>
          </datalist>
          <div class="settings-upload-hint">仅支持 DeepSeek 系模型，具体以 DeepSeek 开放平台为准</div>
        </div>
        <div class="field">
          <label>DeepSeek API Key</label>
          <input v-model="newApiKey" type="password" placeholder="填写自己的 key（自费）；留空不修改">
          <div v-if="apiKeyMasked" class="settings-upload-hint">已配置：{{ apiKeyMasked }}</div>
        </div>
        <div class="settings-row">
          <button class="btn" @click="saveLlmConfig">保存模型配置</button>
          <button v-if="apiKeyMasked" class="btn-outline" @click="clearApiKey">清除已配置 Key</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 560px;
  margin: 40px auto;
  padding: 0 20px
}

.card {
  background: var(--bg-surface, #fff);
  border-radius: 12px;
  padding: 28px 32px;
  box-shadow: 0 4px 24px rgba(30, 41, 59, .08);
  margin-bottom: 20px
}

.card h3 {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 18px
}

.field {
  margin-bottom: 16px
}

.field label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 5px;
  color: var(--text-secondary, #64748B)
}

.field input {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid var(--border, #F0E2EF);
  border-radius: 8px;
  font-size: 14px;
  outline: none
}

.avatar-area {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px
}

.avatar-preview {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid var(--border, #F0E2EF);
  background: var(--bg-body, #FDF2F8)
}

.btn {
  padding: 10px 24px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #EC4899, #F472B6);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer
}

.btn-outline {
  background: transparent;
  color: #0EA5E9;
  border: 1.5px solid #38BDF8;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  transition: all .2s
}

.btn-outline:hover {
  background: rgba(56, 189, 248, .08);
  border-color: #0EA5E9
}

.toast {
  position: fixed;
  top: 16px;
  right: 16px;
  padding: 10px 18px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  z-index: 999;
  background: #22C55E;
  color: #fff
}

.settings-shell {
  flex: 1;
  overflow-y: auto
}

.settings-back-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px
}

.settings-back-link {
  font-size: 13px;
  color: var(--accent);
  text-decoration: none;
  font-weight: 600
}

.settings-title {
  font-size: 16px;
  font-weight: 700
}

.settings-hidden-input {
  display: none
}

.settings-upload-btn {
  font-size: 13px;
  padding: 8px 16px
}

.settings-upload-hint {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 4px
}

.settings-disabled {
  opacity: .5
}

.settings-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 4px
}
</style>
