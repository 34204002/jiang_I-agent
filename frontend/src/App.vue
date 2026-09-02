<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {loadAgentConfig, state} from './stores/state'
import {token} from './utils/storage'
import {loadConversations} from './stores/chat'
import {startReminderPolling} from './utils/reminders'
import Sidebar from './components/Sidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import GraphPanel from './components/GraphPanel.vue'
import KnowledgePanel from './components/KnowledgePanel.vue'
import ToolsPanel from './components/ToolsPanel.vue'
import SettingsView from './views/SettingsView.vue'
import AdminView from './views/AdminView.vue'

const route = useRoute()
const router = useRouter()

// 设置 / 管理后台作为独立弹窗（modal）打开
const settingsOpen = ref(false)
const adminOpen = ref(false)
function openSettings() { settingsOpen.value = true }
function closeSettings() { settingsOpen.value = false }
function openAdmin() { adminOpen.value = true }
function closeAdmin() { adminOpen.value = false }

// 无障碍：Esc 关闭弹窗
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    settingsOpen.value = false
    adminOpen.value = false
  }
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

const isChat = computed(() => route.path === '/chat' || route.path === '/')
const isLogin = computed(() => route.path === '/login')

// chat 主区域四个面板的按需组件（tab 切换用 <Transition> 做淡入淡出动效）
const activePanel = computed(() => {
  if (!isChat.value) return null
  const panels = {chat: ChatPanel, knowledge: KnowledgePanel, graph: GraphPanel, tools: ToolsPanel} as Record<string, typeof ChatPanel>
  return panels[state.activeTab] || null
})

onMounted(() => {
  if (!token.value && !isLogin.value) router.replace('/login')
})

// 登录态变化时加载数据。
// 关键：登录成功是 SPA 内部 router.push('/chat')，App.vue 不会重新 onMounted，
// 若不 watch，登录后会话列表/Agent 配置/提醒轮询都不会触发（只有刷新才加载）。
// immediate:true 同时覆盖"刷新时已登录"的首屏加载。
watch(token, (t) => {
  if (!t) return
  loadConversations()
  loadAgentConfig()
  startReminderPolling()
}, {immediate: true})
</script>

<template>
  <Transition name="page" mode="out-in">
    <div v-if="isLogin" key="login" class="app-shell">
      <router-view/>
    </div>
    <div v-else key="main" class="app-shell">
      <Sidebar @open-settings="openSettings" @open-admin="openAdmin"/>
      <main class="main">
        <nav v-if="isChat" aria-label="主导航" class="tabs" role="tablist">
          <button :class="['tab',{active:state.activeTab==='chat'}]" @click="state.activeTab='chat'"><span
              class="dot"></span>对话
          </button>
          <button :class="['tab',{active:state.activeTab==='knowledge'}]" @click="state.activeTab='knowledge'"><span
              class="dot"></span>知识库
          </button>
          <button :class="['tab',{active:state.activeTab==='graph'}]" @click="state.activeTab='graph'"><span
              class="dot"></span>图谱
          </button>
          <button :class="['tab',{active:state.activeTab==='tools'}]" @click="state.activeTab='tools'"><span
              class="dot"></span>工具
          </button>
        </nav>
        <Transition name="tab" mode="out-in">
          <component :is="activePanel" v-if="isChat && activePanel" :key="'tab-' + state.activeTab"/>
          <router-view v-else-if="!isChat" :key="'page-' + route.path"/>
        </Transition>
      </main>
    </div>
  </Transition>

  <!-- 设置 / 管理后台：独立弹窗，淡入淡出 + 轻微缩放开起 -->
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="settingsOpen" class="modal-mask" @click.self="closeSettings">
        <div class="modal-window" role="dialog" aria-modal="true" aria-label="个人设置" tabindex="-1">
          <SettingsView embedded @close="closeSettings"/>
        </div>
      </div>
    </Transition>
  </Teleport>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="adminOpen" class="modal-mask" @click.self="closeAdmin">
        <div class="modal-window modal-window-lg" role="dialog" aria-modal="true" aria-label="管理后台" tabindex="-1">
          <AdminView embedded @close="closeAdmin"/>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
