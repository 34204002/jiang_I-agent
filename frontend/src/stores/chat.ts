import {activeStreamUrl, state} from './state'
import {api} from '../utils/api'
import type {Conversation, Message, PageResult} from '../types'

export async function loadConversations(): Promise<void> {
    const json = await api.get<PageResult<Conversation>>('/api/conversations?page=1&size=50')
    if (json.code === 200 && json.data) state.convos = json.data.records || []
}

export function newChat(): void {
    activeStreamUrl.value = ''
    state.conversationId = null;
    state.messages = [];
    state.streaming = false;
    state.activeTab = 'chat'
}

export async function selectConvo(id: number | string): Promise<void> {
    activeStreamUrl.value = ''
    state.streaming = false;
    state.conversationId = String(id);
    state.activeTab = 'chat'
    loadConversations()
    const json = await api.get<PageResult<Message>>(`/api/conversations/${id}/messages?page=1&size=200`)
    if (json.code === 200 && json.data) {
        state.messages = json.data.records.map(m => ({
            role: m.role,
            thinking: m.thinking || '',
            content: m.content
        }))
    }
}
