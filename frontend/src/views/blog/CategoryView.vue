<template>
  <div class="category-page page-enter">
    <div class="page-header">
      <span class="mono-label">Browse by</span>
      <h1>Categories</h1>
      <div class="accent-bar"></div>
    </div>

    <div class="category-grid stagger" v-loading="loading">
      <button
        v-for="cat in categories"
        :key="cat.id"
        class="category-card"
        :class="{ active: selectedSlug === cat.slug }"
        @click="selectCategory(cat.slug)"
      >
        <span class="cat-count mono-label">{{ cat.articleCount }} articles</span>
        <h3 class="cat-name">{{ cat.name }}</h3>
        <p class="cat-desc" v-if="cat.description">{{ cat.description }}</p>
        <div class="cat-indicator"></div>
      </button>
    </div>

    <div v-if="selectedSlug" class="filtered-articles">
      <hr class="divider" />
      <div class="section-header">
        <span class="mono-label">Filtered: {{ selectedSlug }}</span>
        <button class="clear-btn mono-label" @click="clearFilter">Clear filter &times;</button>
      </div>
      <div class="article-list stagger">
        <router-link
          v-for="article in articles"
          :key="article.id"
          :to="`/article/${article.slug}`"
          class="article-row"
        >
          <span class="article-date mono-label">{{ formatDate(article.createdAt) }}</span>
          <span class="article-title">{{ article.title }}</span>
          <span class="article-views mono-label">{{ article.viewCount }} views</span>
        </router-link>
        <el-empty v-if="articles.length === 0" description="该分类下暂无文章" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCategories } from '@/api/category'
import { getArticles } from '@/api/article'
import type { CategoryVO } from '@/types/category'
import type { ArticleVO } from '@/types/article'

const route = useRoute()
const router = useRouter()
const categories = ref<CategoryVO[]>([])
const articles = ref<ArticleVO[]>([])
const loading = ref(false)
const selectedSlug = ref(route.query.slug as string || '')

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

async function fetchCategories() {
  loading.value = true
  try {
    const { data } = await getCategories()
    categories.value = data.data
  } finally {
    loading.value = false
  }
}

async function fetchArticlesByCategory(slug: string) {
  if (!slug) { articles.value = []; return }
  const cat = categories.value.find(c => c.slug === slug)
  if (!cat) return
  const { data } = await getArticles({ categoryId: cat.id, current: 1, size: 50 })
  articles.value = data.data.records
}

function selectCategory(slug: string) {
  selectedSlug.value = slug
  router.push({ query: { slug } })
}

function clearFilter() {
  selectedSlug.value = ''
  articles.value = []
  router.push({ query: {} })
}

watch(() => route.query.slug, (slug) => {
  selectedSlug.value = (slug as string) || ''
  fetchArticlesByCategory(selectedSlug.value)
})

onMounted(async () => {
  await fetchCategories()
  if (selectedSlug.value) fetchArticlesByCategory(selectedSlug.value)
})
</script>

<style scoped>
.page-header {
  margin-bottom: var(--space-2xl);
}

.page-header .mono-label {
  display: block;
  margin-bottom: var(--space-sm);
}

.page-header h1 {
  font-size: 2.5rem;
  margin-bottom: var(--space-md);
}

/* ---- Category Grid ---- */
.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-md);
}

.category-card {
  text-align: left;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-lg);
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.category-card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.category-card.active {
  border-color: var(--accent);
  background: var(--accent-light);
}

.cat-count {
  display: block;
  margin-bottom: var(--space-sm);
  color: var(--text-tertiary);
}

.cat-name {
  font-size: 1.25rem;
  margin-bottom: var(--space-xs);
}

.cat-desc {
  font-size: 0.8rem;
  color: var(--text-tertiary);
  line-height: 1.5;
}

.cat-indicator {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--accent);
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.category-card:hover .cat-indicator,
.category-card.active .cat-indicator {
  transform: scaleX(1);
}

/* ---- Filtered Articles ---- */
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-lg);
}

.clear-btn {
  background: none;
  border: 1px solid var(--border);
  padding: 4px 12px;
  cursor: pointer;
  color: var(--text-tertiary);
  border-radius: var(--radius-sm);
  transition: all 0.2s;
}
.clear-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.article-row {
  display: grid;
  grid-template-columns: 80px 1fr 80px;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md) 0;
  border-bottom: 1px solid var(--border-light);
  text-decoration: none;
  color: inherit;
  transition: all 0.2s ease;
}

.article-row:hover {
  padding-left: var(--space-sm);
}

.article-row:hover .article-title {
  color: var(--accent);
}

.article-date {
  color: var(--text-tertiary);
  font-size: 0.65rem;
}

.article-title {
  font-family: var(--font-body);
  font-size: 0.95rem;
  font-weight: 400;
  transition: color 0.2s;
}

.article-views {
  text-align: right;
  color: var(--text-tertiary);
  font-size: 0.6rem;
}
</style>
