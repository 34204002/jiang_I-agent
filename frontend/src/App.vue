<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {loadAgentConfig, state} from './stores/state'
import {token} from './utils/storage'
import {loadConversations} from './stores/chat'
import Sidebar from './components/Sidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import GraphPanel from './components/GraphPanel.vue'
import KnowledgePanel from './components/KnowledgePanel.vue'
import ToolsPanel from './components/ToolsPanel.vue'

const route = useRoute()
const router = useRouter()

const isChat = computed(() => route.path === '/chat' || route.path === '/')
const isLogin = computed(() => route.path === '/login')

onMounted(() => {
  if (!token.value && !isLogin.value) router.replace('/login')
  if (token.value) {
    loadConversations();
    loadAgentConfig()
  }
})
</script>

<template>
  <div v-if="isLogin" class="app-shell">
    <router-view/>
  </div>
  <div v-else class="app-shell">
    <Sidebar/>
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
      <ChatPanel v-if="isChat && state.activeTab==='chat'"/>
      <KnowledgePanel v-if="isChat && state.activeTab==='knowledge'"/>
      <GraphPanel v-if="isChat && state.activeTab==='graph'"/>
      <ToolsPanel v-if="isChat && state.activeTab==='tools'"/>
      <router-view v-if="!isChat"/>
    </main>
  </div>
</template>
