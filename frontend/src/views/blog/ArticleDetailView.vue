<template>
  <div class="article-detail page-enter" v-loading="loading">
    <article v-if="article" class="article-content">
      <!-- Breadcrumb -->
      <div class="breadcrumb">
        <router-link to="/" class="breadcrumb-link">Home</router-link>
        <span class="breadcrumb-sep">/</span>
        <span class="breadcrumb-current">{{ article.categoryName }}</span>
      </div>

      <!-- Article header -->
      <header class="article-header stagger">
        <span class="mono-label">{{ article.categoryName }}</span>
        <h1 class="article-title">{{ article.title }}</h1>
        <p class="article-summary">{{ article.summary }}</p>
        <div class="article-meta">
          <span class="meta-item">{{ formatDate(article.createdAt) }}</span>
          <span class="meta-sep">&middot;</span>
          <span class="meta-item">{{ article.viewCount }} views</span>
          <span class="meta-sep">&middot;</span>
          <span class="meta-item">{{ article.tags?.length || 0 }} tags</span>
        </div>
        <div class="article-tags">
          <span v-for="tag in article.tags" :key="tag.id" class="tag-pill">{{ tag.name }}</span>
        </div>
        <div class="accent-bar" style="margin-top: var(--space-lg)"></div>
      </header>

      <!-- Markdown content -->
      <div class="markdown-body">
        <MdPreview :modelValue="article.content" :theme="'light'" />
      </div>
    </article>

    <hr class="divider" />

    <!-- Comments section -->
    <section class="comments-section stagger">
      <div class="section-header">
        <span class="mono-label">Discussion</span>
        <span class="mono-label">{{ comments.length }} comments</span>
      </div>

      <!-- Comment form -->
      <div class="comment-form">
        <div class="form-row">
          <div class="form-field">
            <label class="field-label mono-label">Nickname</label>
            <input v-model="commentForm.nickname" class="field-input" placeholder="Your name" />
          </div>
          <div class="form-field">
            <label class="field-label mono-label">Email (optional)</label>
            <input v-model="commentForm.email" class="field-input" placeholder="email@example.com" />
          </div>
        </div>
        <div class="form-field">
          <label class="field-label mono-label">Comment</label>
          <textarea v-model="commentForm.content" class="field-textarea" rows="4" placeholder="Share your thoughts..." />
        </div>
        <button class="submit-btn" :disabled="commenting" @click="submitComment">
          {{ commenting ? 'Submitting...' : 'Post Comment' }}
          <span class="btn-arrow">&rarr;</span>
        </button>
      </div>

      <!-- Comment list -->
      <div class="comment-list">
        <div v-for="comment in comments" :key="comment.id" class="comment-item">
          <div class="comment-avatar">{{ comment.nickname[0].toUpperCase() }}</div>
          <div class="comment-body">
            <div class="comment-header">
              <span class="comment-name">{{ comment.nickname }}</span>
              <span class="comment-time mono-label">{{ formatDate(comment.createdAt) }}</span>
            </div>
            <p class="comment-text">{{ comment.content }}</p>

            <!-- Nested replies -->
            <div v-for="child in comment.children" :key="child.id" class="comment-reply">
              <div class="comment-avatar reply-avatar">{{ child.nickname[0].toUpperCase() }}</div>
              <div class="comment-body">
                <div class="comment-header">
                  <span class="comment-name">{{ child.nickname }}</span>
                  <span class="comment-time mono-label">{{ formatDate(child.createdAt) }}</span>
                </div>
                <p class="comment-text">{{ child.content }}</p>
              </div>
            </div>
          </div>
        </div>
        <div v-if="comments.length === 0" class="empty-comments">
          <span class="mono-label">No comments yet. Be the first to share your thoughts.</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { getArticleBySlug } from '@/api/article'
import { getComments, createComment } from '@/api/comment'
import type { ArticleDetailVO } from '@/types/article'
import type { CommentVO, CommentDTO } from '@/types/comment'

const route = useRoute()
const article = ref<ArticleDetailVO | null>(null)
const comments = ref<CommentVO[]>([])
const loading = ref(false)
const commenting = ref(false)

const commentForm = ref<CommentDTO>({ nickname: '', email: '', content: '', parentId: null })

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

async function fetchArticle() {
  loading.value = true
  try {
    const slug = route.params.slug as string
    const { data } = await getArticleBySlug(slug)
    article.value = data.data
    const commentRes = await getComments(data.data.id)
    comments.value = commentRes.data.data
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  if (!article.value) return
  if (!commentForm.value.nickname.trim() || !commentForm.value.content.trim()) {
    ElMessage.warning('Please fill in your nickname and comment')
    return
  }
  commenting.value = true
  try {
    await createComment(article.value.id, commentForm.value)
    ElMessage.success('Comment submitted, awaiting review')
    commentForm.value.content = ''
    fetchArticle()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || 'Failed to submit')
  } finally {
    commenting.value = false
  }
}

onMounted(fetchArticle)
</script>

<style scoped>
/* ---- Breadcrumb ---- */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-xl);
}

.breadcrumb-link {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  letter-spacing: 0.05em;
  color: var(--text-tertiary);
  text-decoration: none;
}
.breadcrumb-link:hover { color: var(--accent); }

.breadcrumb-sep {
  color: var(--text-tertiary);
  font-size: 0.7rem;
}

.breadcrumb-current {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  letter-spacing: 0.05em;
  color: var(--accent);
}

/* ---- Article Header ---- */
.article-header {
  margin-bottom: var(--space-2xl);
}

.article-header .mono-label {
  display: block;
  margin-bottom: var(--space-sm);
  color: var(--accent);
}

.article-title {
  font-size: 2.8rem;
  line-height: 1.1;
  margin-bottom: var(--space-md);
  max-width: 720px;
}

.article-summary {
  font-size: 1.1rem;
  color: var(--text-secondary);
  line-height: 1.7;
  max-width: 600px;
  margin-bottom: var(--space-lg);
}

.article-meta {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.meta-item {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  color: var(--text-tertiary);
  letter-spacing: 0.03em;
}

.meta-sep {
  color: var(--text-tertiary);
  font-size: 0.6rem;
}

.article-tags {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

/* ---- Markdown Body ---- */
.markdown-body {
  max-width: 720px;
  margin: var(--space-2xl) 0;
  line-height: 1.8;
}

.markdown-body :deep(.md-editor-preview) {
  font-family: var(--font-body);
  font-size: 1rem;
  color: var(--text-primary);
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  font-family: var(--font-display);
  margin-top: 2em;
  margin-bottom: 0.5em;
}

.markdown-body :deep(pre) {
  font-family: var(--font-mono);
  background: #1C1A19;
  color: #E8E2D9;
  padding: var(--space-lg);
  border-radius: var(--radius-md);
  overflow-x: auto;
  font-size: 0.85rem;
  line-height: 1.6;
}

.markdown-body :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.85em;
}

.markdown-body :deep(p) {
  margin-bottom: 1.2em;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--accent);
  padding-left: var(--space-lg);
  color: var(--text-secondary);
  font-style: italic;
  font-family: var(--font-display);
  margin: 1.5em 0;
}

/* ---- Comments ---- */
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-xl);
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--border);
}

.comment-form {
  margin-bottom: var(--space-2xl);
  padding: var(--space-lg);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-md);
  margin-bottom: var(--space-md);
}

.form-field {
  margin-bottom: var(--space-md);
}

.field-label {
  display: block;
  margin-bottom: var(--space-xs);
  color: var(--text-tertiary);
}

.field-input,
.field-textarea {
  width: 100%;
  font-family: var(--font-body);
  font-size: 0.9rem;
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-primary);
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.2s;
}

.field-input:focus,
.field-textarea:focus {
  border-color: var(--accent);
}

.field-textarea {
  resize: vertical;
  line-height: 1.6;
}

.submit-btn {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  padding: var(--space-sm) var(--space-lg);
  background: var(--text-primary);
  color: var(--bg-primary);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  transition: all 0.2s ease;
}

.submit-btn:hover {
  background: var(--accent);
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-arrow {
  transition: transform 0.2s;
}

.submit-btn:hover .btn-arrow {
  transform: translateX(4px);
}

/* ---- Comment List ---- */
.comment-item {
  display: flex;
  gap: var(--space-md);
  padding: var(--space-lg) 0;
  border-bottom: 1px solid var(--border-light);
}

.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  font-family: var(--font-mono);
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}

.comment-name {
  font-weight: 500;
  font-size: 0.9rem;
}

.comment-time {
  color: var(--text-tertiary);
  font-size: 0.6rem;
}

.comment-text {
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.7;
}

.comment-reply {
  display: flex;
  gap: var(--space-md);
  margin-top: var(--space-md);
  padding-left: var(--space-md);
  border-left: 2px solid var(--border-light);
}

.reply-avatar {
  width: 28px;
  height: 28px;
  font-size: 0.65rem;
}

.empty-comments {
  padding: var(--space-2xl) 0;
  text-align: center;
}

/* ---- Responsive ---- */
@media (max-width: 768px) {
  .article-title {
    font-size: 2rem;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
