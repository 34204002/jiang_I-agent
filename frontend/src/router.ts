import type {RouteRecordRaw} from 'vue-router'
import {createRouter, createWebHistory} from 'vue-router'
import {readUser, token} from './utils/storage'

function getAuth(): boolean {
    return !!(token.value && readUser().id)
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
        if (readUser().role !== 'ADMIN') return '/chat'
    }
})

export default router
