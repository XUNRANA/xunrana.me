import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/BlogLayout.vue'),
      children: [
        { path: '', name: 'Home', component: () => import('@/views/blog/HomeView.vue') },
        { path: 'article/:slug', name: 'ArticleDetail', component: () => import('@/views/blog/ArticleDetailView.vue') },
        { path: 'category', name: 'Category', component: () => import('@/views/blog/CategoryView.vue') },
        { path: 'tag', name: 'Tag', component: () => import('@/views/blog/TagView.vue') },
        { path: 'archives', name: 'Archives', component: () => import('@/views/blog/ArchivesView.vue') },
      ],
    },
    {
      path: '/admin/login',
      name: 'AdminLogin',
      component: () => import('@/views/admin/LoginView.vue'),
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/admin/DashboardView.vue') },
        { path: 'articles', name: 'ArticleManage', component: () => import('@/views/admin/ArticleManageView.vue') },
        { path: 'categories', name: 'CategoryManage', component: () => import('@/views/admin/CategoryManageView.vue') },
        { path: 'tags', name: 'TagManage', component: () => import('@/views/admin/TagManageView.vue') },
        { path: 'comments', name: 'CommentManage', component: () => import('@/views/admin/CommentManageView.vue') },
        { path: 'logs', name: 'OperationLog', component: () => import('@/views/admin/OperationLogView.vue') },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth) {
    const userStore = useUserStore()
    if (!userStore.token) {
      return '/admin/login'
    }
  }
})

export default router
