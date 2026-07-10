import type {AxiosResponse} from 'axios'
import axios from 'axios'
import type {ApiResponse} from '../types'
import {clearAuth, token} from './storage'

const HTTP_TIMEOUT = 300000

const http = axios.create({
    baseURL: '',
    timeout: HTTP_TIMEOUT, // 5min for SSE-style long responses
    headers: {'Content-Type': 'application/json'}
})

// 请求拦截器：自动注入 auth header
http.interceptors.request.use((config) => {
    if (token.value) {
        config.headers.Authorization = `Bearer ${token.value}`
    }
    return config
})

// 响应拦截器：统一 401 处理
http.interceptors.response.use(
    (response: AxiosResponse<ApiResponse>) => response,
    (error) => {
        if (error.response?.status === 401) {
            clearAuth()
            location.href = '/'
        }
        return Promise.reject(error)
    }
)

// axios 包裹的 Promise<AxiosResponse> 里 data 是 ApiResponse<T>
// 这里解包：直接返回 ApiResponse<T>
async function unwrap<T>(promise: Promise<AxiosResponse<ApiResponse<T>>>): Promise<ApiResponse<T>> {
    try {
        const res = await promise
        return res.data
    } catch (e: unknown) {
        if (axios.isAxiosError(e) && e.response?.status === 401) {
            // 401 已由拦截器处理，这里静默
        }
        const message = axios.isAxiosError(e) ? (e.response?.data as {
            message?: string
        })?.message || e.message : '请求失败'
        return {code: -1, message, data: undefined as unknown as T}
    }
}

export const api = {
    get: <T = unknown>(url: string): Promise<ApiResponse<T>> =>
        unwrap<T>(http.get<ApiResponse<T>>(url)),

    post: <T = unknown>(url: string, body?: unknown): Promise<ApiResponse<T>> =>
        unwrap<T>(http.post<ApiResponse<T>>(url, body)),

    put: <T = unknown>(url: string, body?: unknown): Promise<ApiResponse<T>> =>
        unwrap<T>(http.put<ApiResponse<T>>(url, body)),

    del: <T = unknown>(url: string, body?: unknown): Promise<ApiResponse<T>> =>
        unwrap<T>(http.delete<ApiResponse<T>>(url, {data: body})),

    /** multipart/form-data 上传 */
    postForm: <T = unknown>(url: string, formData: FormData): Promise<ApiResponse<T>> =>
        unwrap<T>(http.post<ApiResponse<T>>(url, formData, {
            headers: {'Content-Type': 'multipart/form-data'}
        }))
}
