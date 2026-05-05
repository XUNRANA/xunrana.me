<template>
  <div class="dashboard page-enter">
    <div class="page-header">
      <span class="mono-label">Overview</span>
      <h2 class="page-title">Dashboard</h2>
      <div class="accent-bar"></div>
    </div>

    <div class="stat-grid stagger">
      <div class="stat-card">
        <span class="stat-label mono-label">Articles</span>
        <span class="stat-value">{{ stats.articles }}</span>
        <div class="stat-indicator"></div>
      </div>
      <div class="stat-card">
        <span class="stat-label mono-label">Categories</span>
        <span class="stat-value">{{ stats.categories }}</span>
        <div class="stat-indicator"></div>
      </div>
      <div class="stat-card">
        <span class="stat-label mono-label">Tags</span>
        <span class="stat-value">{{ stats.tags }}</span>
        <div class="stat-indicator"></div>
      </div>
      <div class="stat-card">
        <span class="stat-label mono-label">Comments</span>
        <span class="stat-value">{{ stats.comments }}</span>
        <div class="stat-indicator"></div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="quick-links stagger">
        <span class="mono-label">Quick actions</span>
        <div class="links-list">
          <router-link to="/admin/articles" class="quick-link">
            <span class="link-name">Manage Articles</span>
            <span class="link-arrow">&rarr;</span>
          </router-link>
          <router-link to="/admin/categories" class="quick-link">
            <span class="link-name">Manage Categories</span>
            <span class="link-arrow">&rarr;</span>
          </router-link>
          <router-link to="/admin/tags" class="quick-link">
            <span class="link-name">Manage Tags</span>
            <span class="link-arrow">&rarr;</span>
          </router-link>
          <router-link to="/admin/comments" class="quick-link">
            <span class="link-name">Review Comments</span>
            <span class="link-arrow">&rarr;</span>
          </router-link>
          <router-link to="/admin/logs" class="quick-link">
            <span class="link-name">Operation Logs</span>
            <span class="link-arrow">&rarr;</span>
          </router-link>
        </div>
      </div>

      <div class="recent-section stagger">
        <span class="mono-label">Recent articles</span>
        <div class="recent-list">
          <div v-for="article in recentArticles" :key="article.id" class="recent-item">
            <div class="recent-info">
              <span class="recent-title">{{ article.title }}</span>
              <span class="recent-meta mono-label">
                {{ article.categoryName }} &middot; {{ formatDate(article.createdAt) }}
              </span>
            </div>
            <span class="recent-status tag-pill" :class="article.status === 1 ? 'published' : 'draft'">
              {{ article.status === 1 ? 'Published' : 'Draft' }}
            </span>
          </div>
          <div v-if="recentArticles.length === 0" class="empty-state">
            <span class="mono-label">No articles yet</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getArticles } from '@/api/article'
import { getCategories } from '@/api/category'
import { getTags } from '@/api/tag'
import type { ArticleVO } from '@/types/article'

const stats = ref({ articles: 0, categories: 0, tags: 0, comments: 0 })
const recentArticles = ref<ArticleVO[]>([])

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

onMounted(async () => {
  try {
    const [articlesRes, categoriesRes, tagsRes] = await Promise.all([
      getArticles({ current: 1, size: 5 }),
      getCategories(),
      getTags(),
    ])
    stats.value.articles = articlesRes.data.data.total
    stats.value.categories = categoriesRes.data.data.length
    stats.value.tags = tagsRes.data.data.length
    recentArticles.value = articlesRes.data.data.records
  } catch {
    // stats remain 0
  }
})
</script>

<style scoped>
.page-header {
  margin-bottom: var(--space-xl);
}

.page-header .mono-label {
  display: block;
  margin-bottom: var(--space-sm);
}

.page-title {
  font-size: 1.75rem;
  margin-bottom: var(--space-md);
}

/* ---- Stat Grid ---- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-md);
  margin-bottom: var(--space-xl);
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-lg);
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
}

.stat-card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-label {
  display: block;
  margin-bottom: var(--space-sm);
}

.stat-value {
  font-family: var(--font-display);
  font-size: 2.5rem;
  font-style: italic;
  color: var(--text-primary);
  line-height: 1;
}

.stat-indicator {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--accent);
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.stat-card:hover .stat-indicator {
  transform: scaleX(1);
}

/* ---- Dashboard Grid ---- */
.dashboard-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--space-xl);
}

/* ---- Quick Links ---- */
.quick-links .mono-label {
  display: block;
  margin-bottom: var(--space-md);
  padding-bottom: var(--space-xs);
  border-bottom: 1px solid var(--border);
}

.links-list {
  display: flex;
  flex-direction: column;
}

.quick-link {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-sm) 0;
  text-decoration: none;
  color: var(--text-secondary);
  font-size: 0.85rem;
  border-bottom: 1px solid var(--border-light);
  transition: all 0.2s ease;
}

.quick-link:hover {
  color: var(--accent);
  padding-left: var(--space-sm);
}

.link-arrow {
  font-family: var(--font-display);
  font-size: 1.1rem;
  color: var(--accent);
  opacity: 0;
  transform: translateX(-4px);
  transition: all 0.2s ease;
}

.quick-link:hover .link-arrow {
  opacity: 1;
  transform: translateX(0);
}

/* ---- Recent Articles ---- */
.recent-section .mono-label {
  display: block;
  margin-bottom: var(--space-md);
  padding-bottom: var(--space-xs);
  border-bottom: 1px solid var(--border);
}

.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-md) 0;
  border-bottom: 1px solid var(--border-light);
}

.recent-title {
  font-size: 0.9rem;
  font-weight: 500;
  margin-bottom: 2px;
}

.recent-meta {
  font-size: 0.6rem;
  color: var(--text-tertiary);
}

.recent-status.published {
  border-color: #5a9e6f;
  color: #5a9e6f;
  background: rgba(90, 158, 111, 0.08);
}

.recent-status.draft {
  border-color: var(--text-tertiary);
  color: var(--text-tertiary);
}

.empty-state {
  padding: var(--space-xl) 0;
  text-align: center;
}

@media (max-width: 900px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
