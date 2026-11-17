<template>
  <el-container style="height: 100%">
    <el-aside width="220px" class="aside">
      <div class="logo">VisionQC</div>
      <el-menu :default-active="route.path" router background-color="#001529" text-color="#b8c7ce"
              active-text-color="#409EFF">
        <el-menu-item index="/">
          <span>控制台</span>
        </el-menu-item>
        <el-menu-item v-if="auth.has('ADMIN')" index="/devices">
          <span>设备管理</span>
        </el-menu-item>
        <el-menu-item v-if="auth.has('ADMIN')" index="/batches">
          <span>生产批次</span>
        </el-menu-item>
        <el-menu-item v-if="auth.has('ADMIN')" index="/images">
          <span>图片管理</span>
        </el-menu-item>
        <el-menu-item v-if="auth.has('INSPECTOR') || auth.has('ADMIN')" index="/my-tasks">
          <span>我的质检任务</span>
        </el-menu-item>
        <el-menu-item v-if="auth.has('REVIEWER') || auth.has('ADMIN')" index="/review">
          <span>审核工作台</span>
        </el-menu-item>
        <el-menu-item v-if="auth.has('REVIEWER') || auth.has('ADMIN')" index="/review-history">
          <span>审核历史</span>
        </el-menu-item>
        <el-menu-item v-if="auth.has('ADMIN') || auth.has('REVIEWER')" index="/stats">
          <span>质量统计看板</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">工业视觉质检与缺陷复核平台</span>
        <div class="right">
          <span>{{ auth.user?.displayName }}（{{ auth.roles.join(',') }}）</span>
          <el-button link type="danger" @click="onLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { logout } from '../api/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

async function onLogout() {
  try { await logout() } catch { /* 已失效也继续本地登出 */ }
  auth.clear()
  router.push('/login')
}
</script>

<style scoped>
.aside { background: #001529; }
.aside .logo { color: #fff; font-size: 20px; font-weight: bold; text-align: center; padding: 20px 0; }
.aside .el-menu { border-right: none; }
.header { background: #fff; display: flex; align-items: center; justify-content: space-between;
          box-shadow: 0 1px 4px rgba(0, 21, 41, .08); }
.title { font-weight: bold; }
.right { display: flex; align-items: center; gap: 12px; }
</style>
