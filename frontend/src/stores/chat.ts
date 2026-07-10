import {state} from '../stores/state'
import {api} from './api'
import type {Message, PageResult} from '../types'

interface ConvoSummary {
    id: number
    title?: string
}

export async function loadConversations(): Promise<void> {
    const json = await api.get<PageResult<ConvoSummary>>('/api/conversations?page=1&size=50')
    if (json.code === 200 && json.data) state.convos = json.data.records || []
}

export function newChat(): void {
    if (window._activeES) {
        window._activeES.close();
        window._activeES = null
    }
    state.conversationId = null;
    state.messages = [];
    state.streaming = false;
    state.activeTab = 'chat'
}

export async function selectConvo(id: number | string): Promise<void> {
    if (window._activeES) {
        window._activeES.close();
        window._activeES = null
    }
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
