<template>
  <div class="tag-manage page-enter">
    <div class="page-header">
      <div class="header-left">
        <span class="mono-label">Taxonomy</span>
        <h2 class="page-title">Tags</h2>
      </div>
      <button class="create-btn" @click="openDialog()">
        <span>+ New Tag</span>
      </button>
    </div>

    <div class="table-container" v-loading="loading">
      <table class="ink-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Slug</th>
            <th class="col-count">Articles</th>
            <th class="col-actions">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tag in tags" :key="tag.id">
            <td>
              <span class="tag-pill">#{{ tag.name }}</span>
            </td>
            <td><span class="mono-label">{{ tag.slug }}</span></td>
            <td class="col-count mono-label">{{ tag.articleCount }}</td>
            <td class="col-actions">
              <button class="action-btn" @click="openDialog(tag)">Edit</button>
              <button class="action-btn danger" @click="handleDelete(tag.id)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="tags.length === 0 && !loading" class="empty-state">
        <span class="mono-label">No tags yet</span>
      </div>
    </div>

    <!-- Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? 'Edit Tag' : 'New Tag'"
      width="420px"
      class="ink-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
        <div class="form-field">
          <label class="field-label mono-label">Name</label>
          <el-input v-model="form.name" placeholder="Tag name" />
        </div>
        <div class="form-field">
          <label class="field-label mono-label">Slug</label>
          <el-input v-model="form.slug" placeholder="url-friendly-slug" />
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
import { ref, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { getTags, createTag, updateTag, deleteTag } from '@/api/tag'
import type { TagVO, TagDTO } from '@/types/tag'

const tags = ref<TagVO[]>([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = ref<TagDTO>({ name: '', slug: '' })
const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  slug: [{ required: true, message: '请输入Slug', trigger: 'blur' }],
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

function openDialog(row?: TagVO) {
  if (row) {
    editingId.value = row.id
    form.value = { name: row.name, slug: row.slug }
  } else {
    editingId.value = null
    form.value = { name: '', slug: '' }
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateTag(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createTag(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchTags()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  if (!confirm('确定删除该标签？')) return
  try {
    await deleteTag(id)
    ElMessage.success('删除成功')
    fetchTags()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

onMounted(fetchTags)
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

.col-count { width: 80px; text-align: center; }
.col-count th { text-align: center; }
.col-actions { width: 130px; }

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

.form-field {
  margin-bottom: var(--space-lg);
}

.field-label {
  display: block;
  margin-bottom: var(--space-xs);
  color: var(--text-tertiary);
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
