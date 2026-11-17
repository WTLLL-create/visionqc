import { defineStore } from 'pinia'

/**
 * 登录态：Pinia + sessionStorage。
 * 页面刷新后从 sessionStorage 恢复，避免"刷新即掉登录"。
 */
export const useAuthStore = defineStore('auth', {
  state: () => {
    let cached = null
    try {
      cached = JSON.parse(sessionStorage.getItem('vqc-user') || 'null')
    } catch { /* 忽略损坏缓存 */ }
    return { user: cached }
  },
  getters: {
    isLoggedIn: (s) => !!s.user,
    roles: (s) => s.user?.roles || [],
    has: (s) => (role) => s.roles.includes(role)
  },
  actions: {
    setUser(user) {
      this.user = user
      sessionStorage.setItem('vqc-user', JSON.stringify(user))
    },
    clear() {
      this.user = null
      sessionStorage.removeItem('vqc-user')
    }
  }
})
