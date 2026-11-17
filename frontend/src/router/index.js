import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/**
 * 路由表与 docs/05-前端页面结构.md 一致。
 * meta.roles：允许访问的角色；空 = 登录即可。
 * 路由守卫与后端 SecurityConfig 双层校验——前端拦截只是体验，后端才是权威。
 */
const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../components/AppLayout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
      { path: 'devices', name: 'devices', component: () => import('../views/DeviceListView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'batches', name: 'batches', component: () => import('../views/BatchListView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'images', name: 'images', component: () => import('../views/ImageView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'annotate/:taskId', name: 'annotate', component: () => import('../views/AnnotateView.vue'), meta: { roles: ['INSPECTOR'] } },
      { path: 'my-tasks', name: 'myTasks', component: () => import('../views/MyTaskListView.vue'), meta: { roles: ['INSPECTOR', 'ADMIN'] } },
      { path: 'review', name: 'review', component: () => import('../views/ReviewView.vue'), meta: { roles: ['REVIEWER', 'ADMIN'] } },
      { path: 'review-history', name: 'reviewHistory', component: () => import('../views/ReviewHistoryView.vue'), meta: { roles: ['REVIEWER', 'ADMIN'] } },
      { path: 'stats', name: 'stats', component: () => import('../views/StatsView.vue'), meta: { roles: ['ADMIN', 'REVIEWER'] } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.isLoggedIn) return { name: 'login' }
  if (to.meta.roles && !to.meta.roles.some((r) => auth.roles.includes(r))) {
    return { name: 'dashboard' } // 无权限角色回首页
  }
  return true
})

export default router
