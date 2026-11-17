<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 160px" @change="load">
          <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="batchId" placeholder="全部批次" clearable filterable style="width: 240px" @change="load">
          <el-option v-for="b in batches" :key="b.id" :label="`${b.batchCode} / ${b.productType}`" :value="b.id" />
        </el-select>
        <el-button @click="load">刷新</el-button>
        <span class="hint">质检员只看分配给自己的任务；管理员可分配任务</span>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="任务ID" width="90" />
      <el-table-column prop="imageId" label="图片ID" width="90" />
      <el-table-column prop="status" label="状态" width="130">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="质检员" width="100">
        <template #default="{ row }">{{ row.inspectorId || '未分配' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="isInspector" link type="primary" @click="router.push(`/annotate/${row.id}`)">
            {{ row.status === 'SUBMITTED' || row.status === 'APPROVED' ? '查看标注' : '打开标注' }}
          </el-button>
          <el-button v-if="isAdmin && row.status === 'NEW'" link type="success" @click="openAssign(row)">
            分配质检员
          </el-button>
          <el-tag v-if="isAdmin && row.status !== 'NEW'" type="info" size="small">已分配</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px" background layout="total, prev, pager, next"
                   :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />

    <el-dialog v-model="assignVisible" title="分配质检员" width="480px">
      <el-form label-width="90px">
        <el-form-item label="任务">
          <span>#{{ assignRow?.id }} / 版本 {{ assignRow?.version }}</span>
        </el-form-item>
        <el-form-item label="质检员" required>
          <el-select v-model="assignInspectorId" placeholder="选择质检员" filterable style="width: 100%">
            <el-option v-for="u in inspectors" :key="u.id" :label="`${u.displayName}（${u.username}）`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assignComment" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" :disabled="!assignInspectorId" @click="doAssign">确认分配</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { listTasks, assignTask, listUsers } from '../api/task'
import { listBatches } from '../api/batch'

const router = useRouter()
const auth = useAuthStore()
const isInspector = computed(() => auth.roles.includes('INSPECTOR'))
const isAdmin = computed(() => auth.roles.includes('ADMIN'))

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const status = ref('')
const batchId = ref(null)
const batches = ref([])
const statusOptions = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'REJECTED', 'APPROVED']

const assignVisible = ref(false)
const assignRow = ref(null)
const assignInspectorId = ref(null)
const assignComment = ref('')
const assigning = ref(false)
const inspectors = ref([])

function statusType(s) {
  return { NEW: 'info', ASSIGNED: 'warning', IN_PROGRESS: 'primary', SUBMITTED: 'warning', REJECTED: 'danger', APPROVED: 'success' }[s] || 'info'
}

async function loadBatches() {
  const res = await listBatches({ page: 0, size: 100 })
  batches.value = res.content
}

async function load() {
  loading.value = true
  try {
    const res = await listTasks({
      status: status.value || undefined,
      batchId: batchId.value || undefined,
      page: page.value - 1,
      size
    })
    rows.value = res.content
    total.value = res.totalElements
  } finally {
    loading.value = false
  }
}

async function openAssign(row) {
  assignRow.value = row
  assignInspectorId.value = null
  assignComment.value = ''
  assignVisible.value = true
  const res = await listUsers({ role: 'INSPECTOR', page: 0, size: 50 })
  inspectors.value = res.content.filter((u) => u.enabled)
}

async function doAssign() {
  assigning.value = true
  try {
    await assignTask(assignRow.value.id, {
      inspectorId: assignInspectorId.value,
      version: assignRow.value.version,
      comment: assignComment.value
    })
    ElMessage.success('分配成功')
    assignVisible.value = false
    await load()
  } finally {
    assigning.value = false
  }
}

onMounted(async () => {
  await loadBatches()
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; }
</style>
