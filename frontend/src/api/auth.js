import request, { ensureCsrf } from './request'

export const login = async (data) => {
  await ensureCsrf() // 登录是写请求，必须先种植 CSRF 令牌
  return request.post('/auth/login', data)
}
export const logout = () => request.post('/auth/logout')
export const me = () => request.get('/auth/me')
