<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>VisionQC 平台登录</h2>
      <p class="sub">工业视觉质检与缺陷复核平台</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="onLogin">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="onLogin">
          登 录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const user = await login(form) // 后端认证成功写 Session Cookie
    auth.setUser(user)
    ElMessage.success(`欢迎，${user.displayName}`)
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center;
              background: linear-gradient(135deg, #1f3a5f 0%, #2878ff 100%); }
.login-card { width: 380px; }
h2 { margin: 0 0 4px; text-align: center; }
.sub { text-align: center; color: #909399; margin: 0 0 20px; font-size: 13px; }
</style>
