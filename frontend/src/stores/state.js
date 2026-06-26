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
export const USER = reactive(JSON.parse(localStorage.getItem('user') || 'null') || {})

export const agent = reactive({ name: 'Jiang I-Agent', avatar: '' })

export async function loadAgentConfig() {
  if (!TOKEN) return
  try {
    const r = await fetch('/api/admin/agent', { headers: { 'Authorization': 'Bearer ' + TOKEN } })
    const json = await r.json()
    if (json.code === 200 && json.data) {
      if (json.data.agentName) agent.name = json.data.agentName
      if (json.data.avatar) agent.avatar = json.data.avatar
    }
  } catch (_) {}
}

export function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  window.location.href = '/'
}
