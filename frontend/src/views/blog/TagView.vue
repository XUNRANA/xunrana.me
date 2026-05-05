<template>
  <div class="tag-page page-enter">
    <div class="page-header">
      <span class="mono-label">Browse by</span>
      <h1>Tags</h1>
      <div class="accent-bar"></div>
    </div>

    <div class="tag-cloud stagger" v-loading="loading">
      <button
        v-for="tag in tags"
        :key="tag.id"
        class="tag-chip"
        :class="{ active: selectedSlug === tag.slug }"
        @click="selectTag(tag.slug)"
      >
        <span class="tag-name">#{{ tag.name }}</span>
        <span class="tag-count">{{ tag.articleCount }}</span>
      </button>
    </div>

    <div v-if="selectedSlug" class="filtered-articles">
      <hr class="divider" />
      <div class="section-header">
        <span class="mono-label">Tagged: #{{ selectedSlug }}</span>
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
        <el-empty v-if="articles.length === 0" description="该标签下暂无文章" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTags } from '@/api/tag'
import { getArticles } from '@/api/article'
import type { TagVO } from '@/types/tag'
import type { ArticleVO } from '@/types/article'

const route = useRoute()
const router = useRouter()
const tags = ref<TagVO[]>([])
const articles = ref<ArticleVO[]>([])
const loading = ref(false)
const selectedSlug = ref(route.query.slug as string || '')

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

async function fetchTags() {
  loading.value = true
  try {
    const { data } = await getTags()
    tags.value = data.data
  } finally {
    loading.value = false
  }
}

async function fetchArticlesByTag(slug: string) {
  if (!slug) { articles.value = []; return }
  const tag = tags.value.find(t => t.slug === slug)
  if (!tag) return
  const { data } = await getArticles({ tagId: tag.id, current: 1, size: 50 })
  articles.value = data.data.records
}

function selectTag(slug: string) {
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
  fetchArticlesByTag(selectedSlug.value)
})

onMounted(async () => {
  await fetchTags()
  if (selectedSlug.value) fetchArticlesByTag(selectedSlug.value)
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

/* ---- Tag Cloud ---- */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 0.8rem;
  padding: 8px 16px;
  border: 1px solid var(--border);
  border-radius: 2px;
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.tag-chip:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-light);
}

.tag-chip.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.tag-name {
  font-weight: 500;
}

.tag-count {
  font-size: 0.65rem;
  opacity: 0.6;
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
