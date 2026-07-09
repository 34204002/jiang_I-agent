// ==================== API ====================

export interface ApiResponse<T = unknown> {
    code: number
    message?: string
    data: T
}

export interface PageResult<T> {
    total: number
    page: number
    size: number
    records: T[]
}

// ==================== Auth ====================

export interface UserInfo {
    id?: number
    username: string
    nickname?: string
    role?: string
    avatar?: string
}

// ==================== Chat ====================

export interface Message {
    role: 'user' | 'assistant'
    content: string
    thinking?: string
}

export interface Conversation {
    id: number
    title?: string
}

// ==================== Knowledge Graph ====================

export interface Concept {
    name: string
    description?: string
    category?: string
    difficulty?: number
    relationCount?: number
}

export interface ConceptDetail extends Concept {
    prerequisites?: Concept[]
    related?: Concept[]
    documents?: { filename: string }[]
}

export interface GraphNode {
    id: string
    label: string
    level?: number
    center?: boolean
    category?: string
}

export interface GraphEdge {
    from: string
    to: string
    label: string
}

export interface GraphPayload {
    nodes: GraphNode[]
    edges: GraphEdge[]
}

// ==================== Todos ====================

export interface TodoItem {
    id: number
    title: string
    isDone: boolean
    dueDate?: string
}

// ==================== Knowledge Base ====================

export interface DocumentItem {
    id: number
    filename: string
    fileType: string
    fileSize: number
    status: number
    summary?: string
    ossKey?: string
    chunkCount?: number
    uploadedAt?: string
    downloadUrl?: string
}

export interface SearchSource {
    filename: string
    content: string
    score: number
    documentId: number | null
    chunkIndex: number | null
}

export interface SearchResponse {
    answer: string
    sources: SearchSource[]
}

// ==================== Agent Config ====================

export interface AgentConfig {
    agentName?: string
    avatar?: string
    model?: string
    temperature?: number
    systemPrompt?: string
}

// ==================== Tools ====================

export interface ToolInfo {
    name: string
    description: string
    category?: string
}

// ==================== SSE Stream ====================

export interface StreamEvent {
    type: 'thinking' | 'content' | 'tool_call'
    content?: string
    name?: string
}
