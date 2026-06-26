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

export const TOKEN = localStorage.getItem('token') || ''
export const AGENT_NAME = window.AGENT_NAME || 'Jiang I-Agent'
export const AGENT_AVATAR = window.AGENT_AVATAR || ''
export const USER = JSON.parse(localStorage.getItem('user') || 'null') || {}

export function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  window.location.href = '/'
}
