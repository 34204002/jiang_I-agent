import type {ApiResponse} from '../types'

const TOKEN = localStorage.getItem('token') || ''

function authHeaders(overrides?: RequestInit): RequestInit {
    const h: RequestInit = {headers: {'Authorization': 'Bearer ' + TOKEN}, ...overrides}
    if (overrides && overrides.headers) {
        const merged = new Headers(overrides.headers as HeadersInit)
        merged.set('Authorization', 'Bearer ' + TOKEN)
        h.headers = merged
    }
    return h
}

function handle<T>(r: Response): Promise<ApiResponse<T>> {
    if (r.status === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        location.href = '/'
    }
    return r.json().then((json: ApiResponse<T>) => {
        if (!r.ok) throw new Error(json.message || '请求失败');
        return json
    })
}

export const api = {
    get: <T = unknown>(url: string): Promise<ApiResponse<T>> =>
        fetch(url, authHeaders()).then(handle<T>).catch((e: Error) => ({
            code: -1,
            message: e.message
        } as ApiResponse<T>)),

    post: <T = unknown>(url: string, body: unknown): Promise<ApiResponse<T>> =>
        fetch(url, authHeaders({
            method: 'POST',
            headers: {'Content-Type': 'application/json'} as unknown as HeadersInit,
            body: JSON.stringify(body)
        })).then(handle<T>).catch((e: Error) => ({code: -1, message: e.message} as ApiResponse<T>)),

    put: <T = unknown>(url: string, body: unknown): Promise<ApiResponse<T>> =>
        fetch(url, authHeaders({
            method: 'PUT',
            headers: {'Content-Type': 'application/json'} as unknown as HeadersInit,
            body: JSON.stringify(body)
        })).then(handle<T>).catch((e: Error) => ({code: -1, message: e.message} as ApiResponse<T>)),

    del: <T = unknown>(url: string, body?: unknown): Promise<ApiResponse<T>> => {
        const opts = authHeaders({method: 'DELETE'})
        if (body) {
            opts.headers = {...opts.headers as Record<string, string>, 'Content-Type': 'application/json'}
            ;(opts as Record<string, unknown>).body = JSON.stringify(body)
        }
        return fetch(url, opts).then(handle<T>).catch((e: Error) => ({code: -1, message: e.message} as ApiResponse<T>))
    },
}
