<script setup>
import { state } from './stores/state'
import { loadConversations } from './utils/chat'
import Sidebar from './components/Sidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import GraphPanel from './components/GraphPanel.vue'
import ToolsPanel from './components/ToolsPanel.vue'

loadConversations()
</script>

<template>
<div class="app-shell">
  <Sidebar />
  <main class="main">
    <nav class="tabs">
      <button :class="['tab', { active: state.activeTab==='chat' }]" @click="state.activeTab='chat'"><span class="dot"></span>对话</button>
      <button :class="['tab', { active: state.activeTab==='graph' }]" @click="state.activeTab='graph'"><span class="dot"></span>图谱</button>
      <button :class="['tab', { active: state.activeTab==='tools' }]" @click="state.activeTab='tools'"><span class="dot"></span>工具</button>
    </nav>
    <ChatPanel v-show="state.activeTab==='chat'" />
    <GraphPanel v-show="state.activeTab==='graph'" />
    <ToolsPanel v-show="state.activeTab==='tools'" />
  </main>
</div>
</template>
