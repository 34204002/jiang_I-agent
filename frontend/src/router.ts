import type {RouteRecordRaw} from 'vue-router'
import {createRouter, createWebHistory} from 'vue-router'

function getAuth(): boolean {
    const t = localStorage.getItem('token')
    const u = localStorage.getItem('user')
    return !!(t && u && u !== 'null')
}

const routes: RouteRecordRaw[] = [
    {path: '/login', name: 'Login', component: () => import('./views/LoginView.vue'), meta: {guest: true}},
    {path: '/settings', name: 'Settings', component: () => import('./views/SettingsView.vue'), meta: {auth: true}},
    {path: '/admin', name: 'Admin', component: () => import('./views/AdminView.vue'), meta: {auth: true, admin: true}},
    {path: '/chat', component: {template: '<div/>'}},
    {path: '/', redirect: '/chat'},
    {path: '/:pathMatch(.*)', redirect: '/chat'},
]

const router = createRouter({
    history: createWebHistory(),
    routes,
})

router.beforeEach((to) => {
    if (to.meta.guest && getAuth()) return '/chat'
    if (to.meta.auth && !getAuth()) return '/login'
    if (to.meta.admin) {
        try {
            const u = JSON.parse(localStorage.getItem('user') || '{}') as { role?: string }
            if (u.role !== 'ADMIN') return '/chat'
        } catch {
            return '/chat'
        }
    }
})

export default router
