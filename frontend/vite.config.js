import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期通过代理解决跨域，生产建议同源部署（Session Cookie 认证最简单）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
