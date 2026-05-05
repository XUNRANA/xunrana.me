<template>
  <div class="home">
    <!-- Hero section -->
    <section class="hero stagger">
      <span class="mono-label">Welcome</span>
      <h1 class="hero-title">
        Thoughts on<br>
        <em>code &amp; systems</em>
      </h1>
      <p class="hero-desc">
        A personal blog documenting my journey through backend development,
        system design, and the craft of building reliable software.
      </p>
      <div class="accent-bar"></div>
    </section>

    <div class="content-grid">
      <!-- Article list -->
      <section class="articles">
        <div class="section-header">
          <span class="mono-label">Latest Articles</span>
          <span class="article-count mono-label">{{ total }} posts</span>
        </div>

        <div class="article-list stagger" v-loading="loading">
          <router-link
            v-for="article in articles"
            :key="article.id"
            :to="`/article/${article.slug}`"
            class="article-card"
          >
            <div class="article-meta-row">
              <span class="mono-label">{{ article.categoryName }}</span>
              <span class="meta-dot">&middot;</span>
              <span class="mono-label">{{ formatDate(article.createdAt) }}</span>
            </div>
            <h2 class="article-title">{{ article.title }}</h2>
            <p class="article-summary">{{ article.summary }}</p>
            <div class="article-footer">
              <div class="article-tags">
                <span v-for="tag in article.tags?.slice(0, 3)" :key="tag.id" class="tag-pill">{{ tag.name }}</span>
              </div>
              <span class="view-count mono-label">{{ article.viewCount }} views</span>
            </div>
            <div class="article-arrow">&rarr;</div>
          </router-link>
          <el-empty v-if="!loading && articles.length === 0" description="暂无文章" />
        </div>

        <div class="pagination-wrap" v-if="total > query.size">
          <button
            v-for="page in totalPages"
            :key="page"
            class="page-btn"
            :class="{ active: page === query.current }"
            @click="query.current = page; fetchArticles()"
          >
            {{ String(page).padStart(2, '0') }}
          </button>
        </div>
      </section>

      <!-- Sidebar -->
      <aside class="sidebar stagger">
        <div class="sidebar-section">
          <span class="mono-label">Categories</span>
          <div class="sidebar-list">
            <router-link
              v-for="cat in categories"
              :key="cat.id"
              :to="`/category?slug=${cat.slug}`"
              class="sidebar-link"
            >
              <span class="sidebar-link-name">{{ cat.name }}</span>
              <span class="sidebar-link-count">{{ cat.articleCount }}</span>
            </router-link>
          </div>
        </div>

        <div class="sidebar-section">
          <span class="mono-label">Tags</span>
          <div class="sidebar-tags">
            <router-link
              v-for="tag in tags"
              :key="tag.id"
              :to="`/tag?slug=${tag.slug}`"
              class="tag-pill"
            >
              {{ tag.name }}
            </router-link>
          </div>
        </div>

        <div class="sidebar-section sidebar-about">
          <span class="mono-label">About</span>
          <p class="about-text">
            Java backend developer in training.
            Building things with Spring Boot, Redis, and curiosity.
          </p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { getArticles } from '@/api/article'
import { getCategories } from '@/api/category'
import { getTags } from '@/api/tag'
import type { ArticleVO } from '@/types/article'
import type { CategoryVO } from '@/types/category'
import type { TagVO } from '@/types/tag'

const articles = ref<ArticleVO[]>([])
const categories = ref<CategoryVO[]>([])
const tags = ref<TagVO[]>([])
const loading = ref(false)
const total = ref(0)
const query = reactive({ current: 1, size: 5 })

const totalPages = computed(() => Math.ceil(total.value / query.size))

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

async function fetchArticles() {
  loading.value = true
  try {
    const { data } = await getArticles(query)
    articles.value = data.data.records
    total.value = data.data.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  fetchArticles()
  const [catRes, tagRes] = await Promise.all([getCategories(), getTags()])
  categories.value = catRes.data.data
  tags.value = tagRes.data.data
})
</script>

<style scoped>
/* ---- Hero ---- */
.hero {
  margin-bottom: var(--space-3xl);
  max-width: 640px;
}

.hero-title {
  font-size: 3.2rem;
  line-height: 1.1;
  margin: var(--space-md) 0 var(--space-lg);
  letter-spacing: -0.03em;
}

.hero-title em {
  font-style: italic;
  color: var(--accent);
}

.hero-desc {
  font-size: 1.05rem;
  color: var(--text-secondary);
  line-height: 1.8;
  max-width: 480px;
}

/* ---- Content Grid ---- */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: var(--space-3xl);
}

/* ---- Section Header ---- */
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-xl);
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--border);
}

.article-count {
  color: var(--text-tertiary);
}

/* ---- Article Cards ---- */
.article-card {
  display: block;
  text-decoration: none;
  color: inherit;
  padding: var(--space-lg) 0;
  border-bottom: 1px solid var(--border-light);
  position: relative;
  transition: all 0.3s ease;
}

.article-card:first-child {
  padding-top: 0;
}

.article-card:hover {
  padding-left: var(--space-md);
}

.article-card:hover .article-arrow {
  opacity: 1;
  transform: translateX(0);
}

.article-card:hover .article-title {
  color: var(--accent);
}

.article-meta-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
}

.meta-dot {
  color: var(--text-tertiary);
  font-size: 0.7rem;
}

.article-title {
  font-size: 1.5rem;
  margin-bottom: var(--space-sm);
  transition: color 0.2s ease;
  line-height: 1.3;
}

.article-summary {
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.7;
  margin-bottom: var(--space-md);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.article-tags {
  display: flex;
  gap: var(--space-sm);
}

.view-count {
  color: var(--text-tertiary);
  font-size: 0.65rem;
}

.article-arrow {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translate(-8px, -50%);
  font-family: var(--font-display);
  font-size: 1.5rem;
  color: var(--accent);
  opacity: 0;
  transition: all 0.3s ease;
}

/* ---- Pagination ---- */
.pagination-wrap {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-xl);
}

.page-btn {
  width: 36px;
  height: 36px;
  border: 1px solid var(--border);
  background: transparent;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: var(--radius-sm);
}

.page-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.page-btn.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

/* ---- Sidebar ---- */
.sidebar {
  position: sticky;
  top: 100px;
  align-self: start;
}

.sidebar-section {
  margin-bottom: var(--space-xl);
}

.sidebar-section .mono-label {
  display: block;
  margin-bottom: var(--space-md);
  padding-bottom: var(--space-xs);
  border-bottom: 1px solid var(--border);
}

.sidebar-list {
  display: flex;
  flex-direction: column;
}

.sidebar-link {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-sm) 0;
  text-decoration: none;
  color: var(--text-secondary);
  font-size: 0.85rem;
  transition: all 0.2s ease;
  border-bottom: 1px solid var(--border-light);
}

.sidebar-link:hover {
  color: var(--accent);
  padding-left: var(--space-sm);
}

.sidebar-link-count {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  color: var(--text-tertiary);
}

.sidebar-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.about-text {
  font-size: 0.85rem;
  color: var(--text-secondary);
  line-height: 1.7;
  font-style: italic;
  font-family: var(--font-display);
  font-size: 1rem;
}

/* ---- Responsive ---- */
@media (max-width: 900px) {
  .content-grid {
    grid-template-columns: 1fr;
    gap: var(--space-xl);
  }

  .sidebar {
    position: static;
  }

  .hero-title {
    font-size: 2.2rem;
  }
}
</style>
