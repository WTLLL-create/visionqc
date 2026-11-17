import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * 统一 axios 实例：
 * - withCredentials 携带 Session Cookie
 * - CSRF：Double Submit，写请求自动带 X-XSRF-TOKEN（令牌来自 XSRF-TOKEN Cookie）
 * - 401 → 跳登录页（后端判定未登录/会话过期）
 * - 409 → 乐观锁/状态冲突，提示"记录已被其他用户修改"（任务书第八节）
 */
const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  withCredentials: true
})

function readCookie(name) {
  const match = document.cookie.match(new RegExp('(^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[2]) : null
}

// 写请求自动附加 CSRF 令牌头（GET/HEAD 不需要）
request.interceptors.request.use((config) => {
  const method = (config.method || 'get').toLowerCase()
  if (!['get', 'head', 'options'].includes(method)) {
    const token = readCookie('XSRF-TOKEN')
    if (token) {
      config.headers['X-XSRF-TOKEN'] = token
    }
  }
  return config
})

request.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const resp = err.response
    let msg = '网络异常，请稍后重试'
    if (resp) {
      msg = resp.data?.message || `请求失败(${resp.status})`
      if (resp.status === 401) {
        router.push('/login')
        msg = '登录已过期，请重新登录'
      }
      if (resp.status === 409) {
        // 版本冲突：后端 GlobalExceptionHandler 返回统一文案
        msg = resp.data?.message || '记录已被其他用户修改，请刷新后查看最新结果'
      }
      if (resp.status === 403) {
        msg = resp.data?.message || '没有操作权限'
      }
      if (resp.status === 413) {
        msg = resp.data?.message || '文件超过大小限制'
      }
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

/** 获取/刷新 CSRF 令牌（登录及任何写操作前调用一次） */
export async function ensureCsrf() {
  await request.get('/auth/csrf')
}

export default request
