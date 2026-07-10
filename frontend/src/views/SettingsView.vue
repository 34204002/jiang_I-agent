<script lang="ts" setup>
import {onMounted, ref, useTemplateRef} from 'vue'
import {api} from '../utils/api'
import {showToast} from '../utils/toast'
import {writeUser} from '../utils/storage'
import type {UserInfo} from '../types'

const username = ref(''), nickname = ref(''), role = ref(''), avatar = ref('')

async function load() {
  const json = await api.get<UserInfo>('/api/user/me')
  if (json.code === 200 && json.data) {
    const u = json.data
    username.value = u.username || ''
    nickname.value = u.nickname || ''
    role.value = u.role || ''
    avatar.value = u.avatar || ''
    writeUser(u)
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
    showToast('保存成功', 'ok')
  } else showToast(json.message || '保存失败', 'error')
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
  color: #EC4899;
  border: 1.5px solid #EC4899;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer
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
</style>
