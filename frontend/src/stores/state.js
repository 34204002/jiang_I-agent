import { reactive } from 'vue'

export const state = reactive({
  conversationId: null,
  messages: [],
  convos: [],
  streaming: false,
  activeTab: 'chat',
  thinking: false,
  toolRunning: null,
  batchMode: false,
  batchSelected: {},
  graphPage: 1,
  graphKeyword: '',
  graphCategory: '',
  graphViewMode: false,
  todoFilter: 'pending',
})

export const AGENT_NAME = window.AGENT_NAME || 'Jiang I-Agent'
export const AGENT_AVATAR = window.AGENT_AVATAR || ''
export const USER = window.USER || {}
export const TOKEN = window.TOKEN || ''
