<script lang="ts" setup>
import {ref} from 'vue'
import {useRouter} from 'vue-router'
import {api} from '../utils/api'
import {token, writeUser} from '../utils/storage'
import {USER} from '../stores/state'

const router = useRouter()
const mode = ref('login')
const username = ref(''), password = ref(''), nickname = ref('')
const confirmPwd = ref('')
const showPwd = ref(false), showConfirmPwd = ref(false)
const msg = ref(''), msgType = ref(''), loading = ref(false)

function toggleMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  confirmPwd.value = ''
  showConfirmPwd.value = false
  msg.value = ''
}

async function submit() {
  if (!username.value.trim() || !password.value) {
    msg.value = '请填写用户名和密码';
    msgType.value = 'error';
    return
  }
  if (mode.value === 'register') {
    if (password.value.length < 6) {
      msg.value = '密码至少 6 位';
      msgType.value = 'error';
      return
    }
    if (password.value !== confirmPwd.value) {
      msg.value = '两次输入的密码不一致';
      msgType.value = 'error';
      return
    }
  }
  loading.value = true
  const url = mode.value === 'login' ? '/api/auth/login' : '/api/auth/register'
  const body = mode.value === 'login'
      ? JSON.stringify({username: username.value.trim(), password: password.value})
      : JSON.stringify({
        username: username.value.trim(),
        password: password.value,
        nickname: nickname.value.trim() || username.value.trim()
      })
  try {
    const json = await api.post(url, JSON.parse(body))
    if (json.code === 200) {
      if (mode.value === 'login') {
        token.value = json.data.token
        writeUser(json.data.user)
        Object.assign(USER, json.data.user)
        msg.value = '登录成功，跳转中…';
        msgType.value = 'ok'
        setTimeout(() => router.push('/chat'), 500)
      } else {
        msg.value = '注册成功，请登录';
        msgType.value = 'ok'
        toggleMode()
      }
    } else {
      msg.value = json.message || '操作失败';
      msgType.value = 'error'
    }
  } catch {
    msg.value = '网络错误';
    msgType.value = 'error'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h1>{{ mode === 'login' ? '登录' : '注册' }}</h1>
      <div class="sub">Jiang I-Agent · 个人 AI 知识库助手</div>
      <div v-if="mode==='register'" class="field"><label>昵称</label><input v-model="nickname"
                                                                            placeholder="给自己取个名字"></div>
      <div class="field"><label>用户名</label><input v-model="username" autocomplete="username" placeholder="输入用户名"
                                                     @keydown.enter="submit"></div>
      <div class="field"><label>密码</label>
        <div class="pwd-input">
          <input v-model="password" autocomplete="current-password" placeholder="输入密码"
                 :type="showPwd ? 'text' : 'password'" @keydown.enter="submit">
          <button class="pwd-toggle" type="button" :title="showPwd ? '隐藏密码' : '显示密码'"
                  @click="showPwd = !showPwd">
            <svg v-if="showPwd" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
              <line x1="1" y1="1" x2="23" y2="23"/>
            </svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
          </button>
        </div>
      </div>
      <div v-if="mode==='register'" class="field"><label>确认密码</label>
        <div class="pwd-input">
          <input v-model="confirmPwd" placeholder="再次输入密码"
                 :type="showConfirmPwd ? 'text' : 'password'" @keydown.enter="submit">
          <button class="pwd-toggle" type="button" :title="showConfirmPwd ? '隐藏密码' : '显示密码'"
                  @click="showConfirmPwd = !showConfirmPwd">
            <svg v-if="showConfirmPwd" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
              <line x1="1" y1="1" x2="23" y2="23"/>
            </svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
          </button>
        </div>
      </div>
      <button :disabled="loading" class="login-btn" @click="submit">
        {{ loading ? '处理中…' : (mode === 'login' ? '登录' : '注册') }}
      </button>
      <div :class="['login-msg', msgType]">{{ msg }}</div>
      <div class="login-switch">{{ mode === 'login' ? '没有账号？' : '已有账号？' }}
        <button class="login-switch-btn" type="button" @click="toggleMode">{{
            mode === 'login' ? '去注册' : '去登录'
          }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  width: 100%;
  background: var(--bg-body, #FDF2F8)
}

.login-card {
  background: #fff;
  border-radius: 12px;
  padding: 40px 36px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 4px 24px rgba(30, 41, 59, .08)
}

.login-card h1 {
  text-align: center;
  font-size: 22px;
  font-weight: 800;
  margin-bottom: 6px
}

.login-card .sub {
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary, #64748B);
  margin-bottom: 28px
}

.field {
  margin-bottom: 16px
}

.field label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 5px
}

.field input {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid var(--border, #F0E2EF);
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color .2s
}

.field input:focus {
  border-color: var(--accent, #EC4899);
  box-shadow: 0 0 0 3px rgba(236, 72, 153, .1)
}

/* ---- 密码显示/隐藏 ---- */
.pwd-input {
  position: relative
}

.pwd-input input {
  padding-right: 40px
}

.pwd-toggle {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: none;
  color: var(--text-tertiary, #94A3B8);
  cursor: pointer;
  border-radius: 6px;
  transition: color .15s, background .15s
}

.pwd-toggle:hover {
  color: var(--accent, #EC4899);
  background: var(--bg-hover, #FDF0F5)
}

.login-btn {
  width: 100%;
  padding: 11px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #EC4899, #F472B6);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  margin-top: 4px
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(236, 72, 153, .3)
}

.login-btn:disabled {
  opacity: .4;
  cursor: not-allowed;
  transform: none
}

.login-msg {
  text-align: center;
  font-size: 13px;
  margin-top: 14px;
  min-height: 20px
}

.login-msg.error {
  color: #EF4444
}

.login-msg.ok {
  color: #22C55E
}

.login-switch {
  text-align: center;
  font-size: 13px;
  color: #64748B;
  margin-top: 20px
}

.login-switch-btn {
  background: none;
  border: none;
  color: var(--accent, #EC4899);
  font-weight: 600;
  cursor: pointer;
  font-size: 13px;
  padding: 0
}
</style>
