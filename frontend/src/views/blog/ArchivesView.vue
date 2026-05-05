<template>
  <div class="archives-page page-enter">
    <div class="page-header">
      <span class="mono-label">All posts</span>
      <h1>Archives</h1>
      <div class="accent-bar"></div>
    </div>

    <div class="archives-content" v-loading="loading">
      <div v-for="(group, year) in groupedArticles" :key="year" class="year-group stagger">
        <div class="year-header">
          <span class="year-number">{{ year }}</span>
          <span class="year-count mono-label">{{ group.length }} posts</span>
        </div>
        <div class="year-articles">
          <router-link
            v-for="article in group"
            :key="article.id"
            :to="`/article/${article.slug}`"
            class="archive-row"
          >
            <span class="archive-date mono-label">{{ formatDate(article.createdAt) }}</span>
            <span class="archive-title">{{ article.title }}</span>
            <span class="archive-category tag-pill">{{ article.categoryName }}</span>
          </router-link>
        </div>
      </div>
      <el-empty v-if="!loading && Object.keys(groupedArticles).length === 0" description="暂无文章" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getArchives } from '@/api/article'
import type { ArticleVO } from '@/types/article'

const articles = ref<ArticleVO[]>([])
const loading = ref(false)

const groupedArticles = computed(() => {
  const groups: Record<string, ArticleVO[]> = {}
  articles.value.forEach(article => {
    const year = article.createdAt.substring(0, 4)
    if (!groups[year]) groups[year] = []
    groups[year].push(article)
  })
  return groups
})

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

async function fetchArchives() {
  loading.value = true
  try {
    const { data } = await getArchives()
    articles.value = data.data
  } finally {
    loading.value = false
  }
}

onMounted(fetchArchives)
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

/* ---- Year Groups ---- */
.year-group {
  margin-bottom: var(--space-2xl);
}

.year-header {
  display: flex;
  align-items: baseline;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
  padding-bottom: var(--space-sm);
  border-bottom: 2px solid var(--text-primary);
}

.year-number {
  font-family: var(--font-display);
  font-size: 2.5rem;
  font-style: italic;
  color: var(--text-primary);
  line-height: 1;
}

.year-count {
  color: var(--text-tertiary);
}

/* ---- Archive Rows ---- */
.archive-row {
  display: grid;
  grid-template-columns: 80px 1fr auto;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md) 0;
  border-bottom: 1px solid var(--border-light);
  text-decoration: none;
  color: inherit;
  transition: all 0.2s ease;
}

.archive-row:hover {
  padding-left: var(--space-sm);
}

.archive-row:hover .archive-title {
  color: var(--accent);
}

.archive-date {
  color: var(--text-tertiary);
  font-size: 0.65rem;
}

.archive-title {
  font-size: 0.95rem;
  font-weight: 400;
  transition: color 0.2s;
}

.archive-category {
  font-size: 0.6rem;
}
</style>
