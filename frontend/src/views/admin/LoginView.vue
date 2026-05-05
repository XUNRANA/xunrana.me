<template>
  <div class="login-page">
    <div class="login-left">
      <div class="login-brand">
        <span class="mono-label">Admin Portal</span>
        <h1 class="brand-title">XUNRANA<span class="accent-dot">.</span></h1>
        <p class="brand-tagline">Backend management console</p>
        <div class="accent-bar"></div>
      </div>
      <div class="login-footer">
        <span class="mono-label">Built with Spring Boot &amp; Vue 3</span>
      </div>
    </div>

    <div class="login-right">
      <div class="login-form-wrapper stagger">
        <span class="mono-label">Sign in</span>
        <h2 class="form-title">Welcome back</h2>
        <p class="form-subtitle">Enter your credentials to access the dashboard.</p>

        <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin" class="login-form">
          <div class="form-field">
            <label class="field-label mono-label">Username</label>
            <el-input
              v-model="form.username"
              placeholder="admin"
              size="large"
              :prefix-icon="User"
              class="ink-input"
            />
          </div>
          <div class="form-field">
            <label class="field-label mono-label">Password</label>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="••••••••"
              size="large"
              :prefix-icon="Lock"
              show-password
              class="ink-input"
              @keyup.enter="handleLogin"
            />
          </div>
          <button class="login-btn" :disabled="loading" @click="handleLogin">
            <span>{{ loading ? 'Signing in...' : 'Sign in' }}</span>
            <span class="btn-arrow">&rarr;</span>
          </button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    await userStore.fetchUserInfo()
    ElMessage.success('登录成功')
    router.push('/admin/dashboard')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: 1fr 1fr;
  height: 100vh;
  overflow: hidden;
}

/* ---- Left: Brand ---- */
.login-left {
  background: #1C1A19;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: var(--space-2xl);
  position: relative;
  overflow: hidden;
}

.login-left::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--accent);
}

.login-brand {
  position: relative;
  z-index: 1;
}

.brand-title {
  font-family: var(--font-display);
  font-size: 3.5rem;
  color: #FAF7F2;
  letter-spacing: -0.03em;
  margin: var(--space-md) 0 var(--space-sm);
}

.accent-dot {
  color: var(--accent);
}

.brand-tagline {
  font-family: var(--font-body);
  font-size: 1rem;
  color: #9C9590;
  margin-bottom: var(--space-lg);
}

.login-footer {
  position: relative;
  z-index: 1;
}

.login-footer .mono-label {
  color: #6B6560;
}

/* ---- Right: Form ---- */
.login-right {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
  position: relative;
}

.login-right::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: var(--noise);
  background-repeat: repeat;
  pointer-events: none;
  opacity: 0.4;
}

.login-form-wrapper {
  width: 380px;
  position: relative;
  z-index: 1;
}

.form-title {
  font-size: 2rem;
  margin: var(--space-sm) 0 var(--space-xs);
}

.form-subtitle {
  font-size: 0.9rem;
  color: var(--text-tertiary);
  margin-bottom: var(--space-xl);
}

.form-field {
  margin-bottom: var(--space-lg);
}

.field-label {
  display: block;
  margin-bottom: var(--space-xs);
  color: var(--text-tertiary);
}

.login-form :deep(.el-input__wrapper) {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  box-shadow: none;
  font-family: var(--font-body);
  padding: 4px 12px;
  transition: border-color 0.2s;
}

.login-form :deep(.el-input__wrapper:hover),
.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent);
  box-shadow: none;
}

.login-form :deep(.el-input__inner) {
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--text-primary);
}

.login-form :deep(.el-input__prefix .el-icon) {
  color: var(--text-tertiary);
}

.login-btn {
  width: 100%;
  font-family: var(--font-mono);
  font-size: 0.8rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  padding: 14px var(--space-lg);
  background: var(--text-primary);
  color: var(--bg-primary);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  margin-top: var(--space-xl);
  transition: all 0.2s ease;
}

.login-btn:hover {
  background: var(--accent);
}

.login-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-arrow {
  transition: transform 0.2s;
}

.login-btn:hover .btn-arrow {
  transform: translateX(4px);
}

@media (max-width: 768px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-left {
    padding: var(--space-xl) var(--space-xl) var(--space-lg);
    min-height: auto;
  }

  .brand-title {
    font-size: 2.5rem;
  }
}
</style>
