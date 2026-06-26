import { createRouter, createWebHistory } from 'vue-router'

function getAuth() {
  const t = localStorage.getItem('token')
  const u = localStorage.getItem('user')
  return t && u && u !== 'null'
}

const routes = [
  { path: '/login', name:'Login', component: () => import('./views/LoginView.vue'), meta: { guest: true } },
  { path: '/settings', name:'Settings', component: () => import('./views/SettingsView.vue'), meta: { auth: true } },
  { path: '/admin', name:'Admin', component: () => import('./views/AdminView.vue'), meta: { auth: true, admin: true } },
  { path: '/', redirect: '/chat' },
  { path: '/:pathMatch(.*)', redirect: '/chat' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.meta.guest && getAuth()) return '/chat'
  if (to.meta.auth && !getAuth()) return '/login'
  if (to.meta.admin) {
    try { const u = JSON.parse(localStorage.getItem('user')||'{}'); if (u.role !== 'ADMIN') return '/chat' }
    catch { return '/chat' }
  }
})

export default router
