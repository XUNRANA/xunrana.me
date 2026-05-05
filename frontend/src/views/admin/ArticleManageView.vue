<template>
  <div class="article-manage page-enter">
    <div class="page-header">
      <div class="header-left">
        <span class="mono-label">Content</span>
        <h2 class="page-title">Articles</h2>
      </div>
      <button class="create-btn" @click="openDialog()">
        <span>+ New Article</span>
      </button>
    </div>

    <div class="table-container" v-loading="loading">
      <table class="ink-table">
        <thead>
          <tr>
            <th class="col-title">Title</th>
            <th class="col-category">Category</th>
            <th class="col-status">Status</th>
            <th class="col-views">Views</th>
            <th class="col-date">Date</th>
            <th class="col-actions">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="article in articles" :key="article.id">
            <td class="col-title">
              <span class="article-title-cell">{{ article.title }}</span>
            </td>
            <td class="col-category">
              <span class="tag-pill">{{ article.categoryName }}</span>
            </td>
            <td class="col-status">
              <span class="status-badge" :class="article.status === 1 ? 'published' : 'draft'">
                {{ article.status === 1 ? 'Published' : 'Draft' }}
              </span>
            </td>
            <td class="col-views mono-label">{{ article.viewCount }}</td>
            <td class="col-date mono-label">{{ formatDate(article.createdAt) }}</td>
            <td class="col-actions">
              <button class="action-btn" @click="openDialog(article)">Edit</button>
              <button class="action-btn danger" @click="handleDelete(article.id)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="articles.length === 0 && !loading" class="empty-state">
        <span class="mono-label">No articles found</span>
      </div>
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

    <!-- Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? 'Edit Article' : 'New Article'"
      width="820px"
      destroy-on-close
      class="ink-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
        <div class="dialog-grid">
          <div class="form-field full">
            <label class="field-label mono-label">Title</label>
            <el-input v-model="form.title" placeholder="Article title" />
          </div>
          <div class="form-field">
            <label class="field-label mono-label">Slug</label>
            <el-input v-model="form.slug" placeholder="url-friendly-slug" />
          </div>
          <div class="form-field">
            <label class="field-label mono-label">Category</label>
            <el-select v-model="form.categoryId" placeholder="Select category" style="width: 100%">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </div>
          <div class="form-field full">
            <label class="field-label mono-label">Tags</label>
            <el-select v-model="form.tagIds" multiple placeholder="Select tags" style="width: 100%">
              <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
          </div>
          <div class="form-field full">
            <label class="field-label mono-label">Summary</label>
            <el-input v-model="form.summary" type="textarea" :rows="2" placeholder="Brief description" />
          </div>
          <div class="form-field full">
            <label class="field-label mono-label">Content</label>
            <MdEditor v-model="form.content" style="height: 400px" @onUploadImg="handleUploadImg" />
          </div>
          <div class="form-field full">
            <label class="field-label mono-label">Status</label>
            <div class="radio-group">
              <button
                class="radio-btn"
                :class="{ active: form.status === 0 }"
                @click="form.status = 0"
              >Draft</button>
              <button
                class="radio-btn"
                :class="{ active: form.status === 1 }"
                @click="form.status = 1"
              >Published</button>
            </div>
          </div>
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <button class="cancel-btn" @click="dialogVisible = false">Cancel</button>
          <button class="submit-btn" :disabled="submitting" @click="handleSubmit">
            {{ submitting ? 'Saving...' : 'Save' }}
            <span class="btn-arrow">&rarr;</span>
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { getArticles, createArticle, updateArticle, deleteArticle } from '@/api/article'
import { getCategories } from '@/api/category'
import { getTags } from '@/api/tag'
import { uploadImage } from '@/api/file'
import type { ArticleVO, ArticleDTO } from '@/types/article'
import type { CategoryVO } from '@/types/category'
import type { TagVO } from '@/types/tag'

const articles = ref<ArticleVO[]>([])
const categories = ref<CategoryVO[]>([])
const tags = ref<TagVO[]>([])
const loading = ref(false)
const submitting = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const query = reactive({ current: 1, size: 10 })
const totalPages = computed(() => Math.ceil(total.value / query.size))

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

const defaultForm = (): ArticleDTO => ({
  title: '', slug: '', summary: '', content: '', coverImage: '', categoryId: 0, tagIds: [], status: 0,
})
const form = ref<ArticleDTO>(defaultForm())

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  slug: [{ required: true, message: '请输入Slug', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
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

async function fetchOptions() {
  const [catRes, tagRes] = await Promise.all([getCategories(), getTags()])
  categories.value = catRes.data.data
  tags.value = tagRes.data.data
}

function openDialog(row?: ArticleVO) {
  if (row) {
    editingId.value = row.id
    form.value = {
      title: row.title, slug: row.slug, summary: row.summary, content: '',
      coverImage: row.coverImage, categoryId: row.categoryId,
      tagIds: row.tags?.map(t => t.id) || [], status: row.status,
    }
  } else {
    editingId.value = null
    form.value = defaultForm()
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateArticle(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createArticle(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchArticles()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  if (!confirm('确定删除该文章？')) return
  try {
    await deleteArticle(id)
    ElMessage.success('删除成功')
    fetchArticles()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

async function handleUploadImg(files: File[], callback: (urls: string[]) => void) {
  const urls = await Promise.all(
    files.map(async (file) => {
      const { data } = await uploadImage(file)
      return data.data.url
    })
  )
  callback(urls)
}

onMounted(() => {
  fetchArticles()
  fetchOptions()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: var(--space-xl);
}

.page-header .mono-label {
  display: block;
  margin-bottom: var(--space-sm);
}

.page-title {
  font-size: 1.75rem;
}

.create-btn {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  padding: 10px 20px;
  background: var(--text-primary);
  color: var(--bg-primary);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
}

.create-btn:hover {
  background: var(--accent);
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

.col-title { min-width: 200px; }
.col-category { width: 120px; }
.col-status { width: 90px; }
.col-views { width: 70px; }
.col-date { width: 90px; }
.col-actions { width: 130px; }

.article-title-cell {
  font-weight: 500;
  color: var(--text-primary);
}

.status-badge {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  padding: 3px 8px;
  border-radius: 2px;
}

.status-badge.published {
  background: rgba(90, 158, 111, 0.1);
  color: #5a9e6f;
}

.status-badge.draft {
  background: var(--bg-secondary);
  color: var(--text-tertiary);
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

.action-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.action-btn.danger:hover {
  border-color: #c53030;
  color: #c53030;
}

.empty-state {
  padding: var(--space-2xl);
  text-align: center;
}

/* ---- Pagination ---- */
.pagination-wrap {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
  justify-content: flex-end;
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

/* ---- Dialog ---- */
.ink-dialog :deep(.el-dialog) {
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
}

.ink-dialog :deep(.el-dialog__header) {
  padding: var(--space-lg) var(--space-lg) var(--space-md);
  border-bottom: 1px solid var(--border-light);
}

.ink-dialog :deep(.el-dialog__title) {
  font-family: var(--font-display);
  font-size: 1.25rem;
}

.ink-dialog :deep(.el-dialog__body) {
  padding: var(--space-lg);
}

.ink-dialog :deep(.el-dialog__footer) {
  padding: var(--space-md) var(--space-lg) var(--space-lg);
  border-top: 1px solid var(--border-light);
}

.dialog-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-md);
}

.form-field.full {
  grid-column: 1 / -1;
}

.field-label {
  display: block;
  margin-bottom: var(--space-xs);
  color: var(--text-tertiary);
}

.radio-group {
  display: flex;
  gap: var(--space-sm);
}

.radio-btn {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  padding: 8px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.radio-btn.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.radio-btn:hover:not(.active) {
  border-color: var(--accent);
  color: var(--accent);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
}

.cancel-btn {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  padding: 10px 20px;
  background: transparent;
  color: var(--text-tertiary);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
}

.cancel-btn:hover {
  border-color: var(--text-secondary);
  color: var(--text-secondary);
}

.submit-btn {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  padding: 10px 20px;
  background: var(--text-primary);
  color: var(--bg-primary);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  transition: all 0.2s;
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
</style>
