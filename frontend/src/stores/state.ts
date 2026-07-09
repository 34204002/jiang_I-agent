import {reactive} from 'vue'
import type {UserInfo} from '../types'
import {api} from '../utils/api'

export const state = reactive({
    conversationId: null as string | null,
    messages: [] as { role: string; content: string; thinking?: string }[],
    convos: [] as { id: number; title?: string }[],
    streaming: false,
    activeTab: 'chat' as string,
    thinking: false,
    toolRunning: null as string | null,
    batchMode: false,
    batchSelected: {} as Record<string, boolean>,
    graphPage: 1,
    graphKeyword: '',
    graphCategory: '',
    graphViewMode: false,
    todoFilter: 'pending' as string,
})

function safeJsonParse<T>(raw: string | null, fallback: T): T {
    try {
        return (JSON.parse(raw || '') || fallback) as T
    } catch {
        return fallback
    }
}

export const TOKEN: string = localStorage.getItem('token') || ''

const storedUser = safeJsonParse<Record<string, unknown>>(localStorage.getItem('user'), {})
export const USER: UserInfo = reactive<UserInfo>({
    id: storedUser.id as number | undefined,
    username: storedUser.username as string || '',
    nickname: storedUser.nickname as string | undefined,
    role: storedUser.role as string | undefined,
    avatar: storedUser.avatar as string | undefined,
})

export const agent = reactive<{ name: string; avatar: string }>({name: 'Jiang I-Agent', avatar: ''})

export async function loadAgentConfig(): Promise<void> {
    if (!TOKEN) return
    try {
        const json = await api.get<{ agentName?: string; avatar?: string }>('/api/admin/agent')
        if (json.code === 200 && json.data) {
            if (json.data.agentName) agent.name = json.data.agentName
            if (json.data.avatar) agent.avatar = json.data.avatar
        }
    } catch (e) {
        console.error('加载 Agent 配置失败:', e)
    }
}

export function logout(): void {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    window.location.href = '/'
}
