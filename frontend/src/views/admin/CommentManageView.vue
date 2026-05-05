<template>
  <div class="comment-manage page-enter">
    <div class="page-header">
      <span class="mono-label">Moderation</span>
      <h2 class="page-title">Comments</h2>
      <div class="accent-bar"></div>
    </div>

    <div class="table-container" v-loading="loading">
      <table class="ink-table">
        <thead>
          <tr>
            <th class="col-article">Article</th>
            <th class="col-nickname">Nickname</th>
            <th class="col-content">Content</th>
            <th class="col-status">Status</th>
            <th class="col-date">Date</th>
            <th class="col-actions">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="comment in comments" :key="comment.id">
            <td class="col-article">
              <span class="article-ref">{{ comment.articleTitle }}</span>
            </td>
            <td class="col-nickname">
              <div class="nickname-cell">
                <span class="avatar-mini">{{ comment.nickname[0].toUpperCase() }}</span>
                <span>{{ comment.nickname }}</span>
              </div>
            </td>
            <td class="col-content">
              <span class="content-preview">{{ comment.content }}</span>
            </td>
            <td class="col-status">
              <span class="status-badge" :class="statusClass(comment.status)">
                {{ statusText(comment.status) }}
              </span>
            </td>
            <td class="col-date mono-label">{{ formatDate(comment.createdAt) }}</td>
            <td class="col-actions">
              <button
                v-if="comment.status !== 1"
                class="action-btn approve"
                @click="handleReview(comment.id, 1)"
              >Approve</button>
              <button
                v-if="comment.status !== 2"
                class="action-btn danger"
                @click="handleReview(comment.id, 2)"
              >Reject</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="comments.length === 0 && !loading" class="empty-state">
        <span class="mono-label">No comments to review</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import type { Result, PageResult } from '@/types/api'
import type { CommentVO } from '@/types/comment'
import { updateCommentStatus } from '@/api/comment'

const comments = ref<CommentVO[]>([])
const loading = ref(false)

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

function statusText(status: number) {
  return status === 1 ? 'Approved' : status === 2 ? 'Rejected' : 'Pending'
}

function statusClass(status: number) {
  return status === 1 ? 'approved' : status === 2 ? 'rejected' : 'pending'
}

async function fetchComments() {
  loading.value = true
  try {
    const { data } = await request.get<Result<PageResult<CommentVO>>>('/v1/admin/comments', { params: { current: 1, size: 50 } })
    comments.value = data.data.records
  } finally {
    loading.value = false
  }
}

async function handleReview(id: number, status: number) {
  try {
    await updateCommentStatus(id, status)
    ElMessage.success('审核成功')
    fetchComments()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '审核失败')
  }
}

onMounted(fetchComments)
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

/* ---- Table ---- */
.table-container {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.ink-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.ink-table th {
  font-family: var(--font-mono);
  font-size: 0.65rem;
  font-weight: 500;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  text-align: left;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--border);
  background: var(--bg-secondary);
}

.ink-table td {
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--border-light);
  color: var(--text-secondary);
  vertical-align: middle;
}

.ink-table tr:hover td {
  background: var(--accent-subtle);
}

.col-article { min-width: 140px; }
.col-nickname { width: 120px; }
.col-content { min-width: 200px; }
.col-status { width: 90px; }
.col-date { width: 90px; }
.col-actions { width: 140px; }

.article-ref {
  font-size: 0.8rem;
  color: var(--text-primary);
  font-weight: 500;
}

.nickname-cell {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.avatar-mini {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  font-weight: 500;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.content-preview {
  font-size: 0.8rem;
  color: var(--text-tertiary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.status-badge {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  padding: 3px 8px;
  border-radius: 2px;
}

.status-badge.approved {
  background: rgba(90, 158, 111, 0.1);
  color: #5a9e6f;
}

.status-badge.rejected {
  background: rgba(197, 48, 48, 0.1);
  color: #c53030;
}

.status-badge.pending {
  background: rgba(196, 85, 58, 0.08);
  color: var(--accent);
}

.action-btn {
  font-family: var(--font-mono);
  font-size: 0.65rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  padding: 4px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  margin-right: 6px;
  transition: all 0.2s;
}

.action-btn.approve:hover {
  border-color: #5a9e6f;
  color: #5a9e6f;
}

.action-btn.danger:hover {
  border-color: #c53030;
  color: #c53030;
}

.empty-state {
  padding: var(--space-2xl);
  text-align: center;
}
</style>
