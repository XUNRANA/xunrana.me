<template>
  <el-container class="admin-layout">
    <el-aside :width="appStore.sidebarCollapsed ? '56px' : '200px'" class="admin-aside">
      <div class="aside-logo">
        <span class="logo-mark" v-if="!appStore.sidebarCollapsed">
          <span class="logo-x">X</span>BLOG
        </span>
        <span class="logo-mark" v-else>
          <span class="logo-x">X</span>
        </span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="appStore.sidebarCollapsed"
        router
        class="aside-menu"
        background-color="transparent"
        text-color="#8A8580"
        active-text-color="#C4553A"
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><Monitor /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        <el-menu-item index="/admin/articles">
          <el-icon><Document /></el-icon>
          <template #title>文章</template>
        </el-menu-item>
        <el-menu-item index="/admin/categories">
          <el-icon><Folder /></el-icon>
          <template #title>分类</template>
        </el-menu-item>
        <el-menu-item index="/admin/tags">
          <el-icon><PriceTag /></el-icon>
          <template #title>标签</template>
        </el-menu-item>
        <el-menu-item index="/admin/comments">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>评论</template>
        </el-menu-item>
        <el-menu-item index="/admin/logs">
          <el-icon><Notebook /></el-icon>
          <template #title>日志</template>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer" v-if="!appStore.sidebarCollapsed">
        <span class="mono-label">Blog Admin v1.0</span>
      </div>
    </el-aside>

    <el-container class="admin-right">
      <el-header class="admin-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="appStore.toggleSidebar">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <span class="header-title mono-label">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-badge">
              <span class="user-avatar">{{ (userStore.userInfo?.username || 'A')[0].toUpperCase() }}</span>
              <span class="user-name">{{ userStore.userInfo?.nickname || userStore.userInfo?.username || 'Admin' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="blog">查看博客</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="admin-main page-enter">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import {
  Monitor, Document, Folder, PriceTag, ChatDotRound, Notebook,
  Fold, Expand
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

userStore.fetchUserInfo()

const titleMap: Record<string, string> = {
  '/admin/dashboard': 'DASHBOARD',
  '/admin/articles': 'ARTICLES',
  '/admin/categories': 'CATEGORIES',
  '/admin/tags': 'TAGS',
  '/admin/comments': 'COMMENTS',
  '/admin/logs': 'OPERATION LOG',
}

const currentTitle = computed(() => titleMap[route.path] || 'ADMIN')

function handleCommand(command: string) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/admin/login')
  } else if (command === 'blog') {
    window.open('/', '_blank')
  }
}
</script>

<style scoped>
.admin-layout {
  height: 100vh;
}

/* ---- Aside ---- */
.admin-aside {
  background: #1C1A19;
  transition: width 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #2A2725;
}

.aside-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #2A2725;
}

.logo-mark {
  font-family: var(--font-mono);
  font-size: 0.85rem;
  font-weight: 500;
  letter-spacing: 0.15em;
  color: #E8E2D9;
}

.logo-x {
  color: var(--accent);
  font-size: 1rem;
}

.aside-menu {
  flex: 1;
  border-right: none;
  padding-top: var(--space-sm);
}

.aside-menu .el-menu-item {
  font-family: var(--font-body);
  font-size: 0.82rem;
  height: 44px;
  line-height: 44px;
  margin: 2px 8px;
  border-radius: var(--radius-sm);
}

.aside-menu .el-menu-item:hover {
  background: #2A2725 !important;
}

.aside-menu .el-menu-item.is-active {
  background: rgba(196, 85, 58, 0.12) !important;
  color: var(--accent) !important;
}

.aside-footer {
  padding: var(--space-md);
  border-top: 1px solid #2A2725;
  text-align: center;
}

.aside-footer .mono-label {
  color: #4A4540;
  font-size: 0.6rem;
}

/* ---- Header ---- */
.admin-right {
  background: var(--bg-primary);
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border);
  background: var(--bg-primary);
  height: 56px;
  padding: 0 var(--space-xl);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.collapse-btn {
  font-size: 18px;
  cursor: pointer;
  color: var(--text-tertiary);
  transition: color 0.2s;
}
.collapse-btn:hover {
  color: var(--text-primary);
}

.header-title {
  font-size: 0.65rem;
  letter-spacing: 0.2em;
}

.user-badge {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: background 0.2s;
}
.user-badge:hover {
  background: var(--bg-secondary);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  font-size: 0.82rem;
  color: var(--text-secondary);
}

/* ---- Main ---- */
.admin-main {
  background: var(--bg-secondary);
  padding: var(--space-lg);
  overflow-y: auto;
}

/* ---- Override Element Plus menu collapse transition ---- */
.aside-menu:not(.el-menu--collapse) {
  width: 184px;
}
</style>
