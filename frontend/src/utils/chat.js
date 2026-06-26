import { state } from '../stores/state'
import { api } from './api'

export async function loadConversations() {
  const json = await api.get('/api/conversations?page=1&size=50')
  if (json.code === 200 && json.data) state.convos = json.data.records || []
}

export function newChat() {
  if (window._activeES) { window._activeES.close(); window._activeES = null }
  state.conversationId = null; state.messages = []; state.streaming = false; state.activeTab = 'chat'
}

export async function selectConvo(id) {
  if (window._activeES) { window._activeES.close(); window._activeES = null }
  state.streaming = false; state.conversationId = String(id); state.activeTab = 'chat'
  loadConversations()
  const json = await api.get(`/api/conversations/${id}/messages?page=1&size=200`)
  if (json.code === 200 && json.data) state.messages = json.data.records.map(m => ({ role: m.role, content: m.content }))
}
