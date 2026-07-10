import {reactive, ref} from 'vue'
import type {UserInfo} from '../types'
import {api} from '../utils/api'
import {clearAuth, readUser, token} from '../utils/storage'

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

export {token}

export const USER: UserInfo = reactive<UserInfo>({ ...readUser() })

/** SSE 流式连接：设置为 URL 即建立连接，设为空字符串即断开 */
export const activeStreamUrl = ref('')

export const agent = reactive<{ name: string; avatar: string }>({name: 'Jiang I-Agent', avatar: ''})

export async function loadAgentConfig(): Promise<void> {
    if (!token.value) return
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
    clearAuth()
    window.location.href = '/'
}
