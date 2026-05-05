<template>
  <div class="operation-log page-enter">
    <div class="page-header">
      <span class="mono-label">System</span>
      <h2 class="page-title">Operation Logs</h2>
      <div class="accent-bar"></div>
    </div>

    <div class="table-container" v-loading="loading">
      <table class="ink-table">
        <thead>
          <tr>
            <th class="col-module">Module</th>
            <th class="col-operation">Operation</th>
            <th class="col-user">User</th>
            <th class="col-ip">IP</th>
            <th class="col-date">Time</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td class="col-module">
              <span class="module-badge">{{ log.module }}</span>
            </td>
            <td class="col-operation">{{ log.operation }}</td>
            <td class="col-user mono-label">{{ log.username }}</td>
            <td class="col-ip mono-label">{{ log.ip }}</td>
            <td class="col-date mono-label">{{ formatDate(log.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="logs.length === 0 && !loading" class="empty-state">
        <span class="mono-label">No operation logs</span>
      </div>
    </div>

    <div class="pagination-wrap" v-if="total > query.size">
      <button
        v-for="page in totalPages"
        :key="page"
        class="page-btn"
        :class="{ active: page === query.current }"
        @click="query.current = page; fetchLogs()"
      >
        {{ String(page).padStart(2, '0') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { getOperationLogs } from '@/api/operationLog'
import type { OperationLogVO } from '@/types/operationLog'

const logs = ref<OperationLogVO[]>([])
const loading = ref(false)
const total = ref(0)
const query = reactive({ current: 1, size: 20 })
const totalPages = computed(() => Math.ceil(total.value / query.size))

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

async function fetchLogs() {
  loading.value = true
  try {
    const { data } = await getOperationLogs(query)
    logs.value = data.data.records
    total.value = data.data.total
  } finally {
    loading.value = false
  }
}

onMounted(fetchLogs)
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

.col-module { width: 120px; }
.col-operation { width: 120px; }
.col-user { width: 100px; }
.col-ip { width: 140px; }
.col-date { width: 160px; }

.module-badge {
  font-family: var(--font-mono);
  font-size: 0.65rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  padding: 3px 8px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 2px;
  color: var(--text-secondary);
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
</style>
