<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { state, USER } from './stores/state'
import { loadConversations } from './utils/chat'
import Sidebar from './components/Sidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import GraphPanel from './components/GraphPanel.vue'
import ToolsPanel from './components/ToolsPanel.vue'

const route = useRoute()
const router = useRouter()

const isLogin = computed(() => route.path === '/login')
const showSidebar = computed(() => !isLogin.value)
const isMainLayout = computed(() => route.path === '/chat' || route.path === '/')

// Init
const token = localStorage.getItem('token')
if (token && isLogin.value) router.replace('/chat')
if (token) loadConversations()
if (!token && !isLogin.value) router.replace('/login')
</script>

<template>
<div v-if="isLogin" class="app-shell">
  <router-view />
</div>
<div v-else class="app-shell">
  <Sidebar />
  <main class="main">
    <nav class="tabs" v-if="route.path==='/chat' || route.path==='/'">
      <button :class="['tab',{active:state.activeTab==='chat'}]" @click="state.activeTab='chat'"><span class="dot"></span>对话</button>
      <button :class="['tab',{active:state.activeTab==='graph'}]" @click="state.activeTab='graph'"><span class="dot"></span>图谱</button>
      <button :class="['tab',{active:state.activeTab==='tools'}]" @click="state.activeTab='tools'"><span class="dot"></span>工具</button>
    </nav>
    <!-- Chat main view -->
    <template v-if="route.path==='/chat' || route.path==='/'">
      <ChatPanel v-show="state.activeTab==='chat'" />
      <GraphPanel v-show="state.activeTab==='graph'" />
      <ToolsPanel v-show="state.activeTab==='tools'" />
    </template>
    <!-- Other pages (settings, admin) -->
    <router-view v-else />
  </main>
</div>
</template>
