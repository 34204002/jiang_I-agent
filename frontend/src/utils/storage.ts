import {useStorage} from '@vueuse/core'
import {safeJsonParse} from './helpers'
import type {UserInfo} from '../types'

/** 登录 token，响应式并与 localStorage 双向同步 */
export const token = useStorage('token', '')

export function readUser(): UserInfo {
    return safeJsonParse<UserInfo>(localStorage.getItem('user'), {} as UserInfo)
}

export function writeUser(u: UserInfo): void {
    localStorage.setItem('user', JSON.stringify(u))
}

export function clearAuth(): void {
    token.value = null
    localStorage.removeItem('user')
}
