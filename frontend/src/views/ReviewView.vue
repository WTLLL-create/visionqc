<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px" @change="load">
          <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="batchId" placeholder="全部批次" clearable filterable style="width: 230px" @change="load">
          <el-option v-for="b in batches" :key="b.id" :label="`${b.batchCode} / ${b.productType}`" :value="b.id" />
        </el-select>
        <el-button @click="load">刷新</el-button>
        <el-switch v-model="autoRefresh" active-text="自动刷新 30s" />
        <span class="hint">待审核任务实时来自数据库；审核操作会推进状态并写入审核历史</span>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" border stripe @row-click="openDetail">
      <el-table-column prop="id" label="任务ID" width="90" />
      <el-table-column prop="imageId" label="图片ID" width="90" />
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="质检员" width="100">
        <template #default="{ row }">{{ row.inspectorId || '未分配' }}</template>
      </el-table-column>
      <el-table-column label="审核员" width="100">
        <template #default="{ row }">{{ row.reviewerId || '—' }}</template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openDetail(row)">查看 / 审核</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px" background layout="total, prev, pager, next"
                   :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
  </el-card>

  <el-drawer v-model="detailVisible" size="72%" :title="`审核任务 #${current?.id || ''}`">
    <div v-loading="detailLoading">
      <div class="detail-head" v-if="current">
        <el-tag :type="statusType(current.status)" size="large">{{ current.status }}</el-tag>
        <span class="meta">版本 {{ current.version }}</span>
        <span class="meta">图片 #{{ current.imageId }}</span>
        <span v-if="image" class="meta">{{ image.width }} × {{ image.height }}</span>
      </div>

      <el-alert v-if="auth.has('ADMIN') && !auth.has('REVIEWER')" type="info" show-icon :closable="false"
                title="管理员可查看审核信息，但审批动作只能由审核员完成" style="margin-bottom: 12px" />

      <div v-if="image" class="preview-wrap">
        <div class="image-box">
          <img :src="imageFileUrl(image.id)" :alt="image.originalName" />
          <div v-for="(a, idx) in annotations" :key="a.id" class="defect-box" :style="boxStyle(a)">
            <span class="defect-label">#{{ idx + 1 }} {{ categoryName(a.categoryId) }}</span>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无图片信息" :image-size="70" />

      <div class="note-list" v-if="annotations.length">
        <div v-for="(a, idx) in annotations" :key="'n' + a.id" class="note-item">
          <b>#{{ idx + 1 }}</b> {{ categoryName(a.categoryId) }}
          <span class="meta">x={{ Number(a.xNorm).toFixed(3) }}, y={{ Number(a.yNorm).toFixed(3) }},
            w={{ Number(a.wNorm).toFixed(3) }}, h={{ Number(a.hNorm).toFixed(3) }}</span>
          <span v-if="a.note"> · {{ a.note }}</span>
        </div>
      </div>
      <el-empty v-else description="该任务没有标注框" :image-size="60" />

      <el-divider content-position="left">审核历史（{{ history.length }}）</el-divider>
      <el-timeline v-if="history.length">
        <el-timeline-item v-for="h in history" :key="h.id" :timestamp="formatTime(h.createdAt)"
                          :type="historyType(h.action)">
          <div class="history-line">
            <b>{{ actionText(h.action) }}</b>
            <span>{{ h.operatorName }}</span>
            <span class="meta">{{ h.fromStatus }} → {{ h.toStatus }}</span>
            <el-tag size="small" type="info">v{{ h.version }}</el-tag>
          </div>
          <div v-if="h.comment" class="history-comment">{{ h.comment }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无审核记录" :image-size="60" />

      <div v-if="canReview" class="actions">
        <el-button type="success" @click="openAction('approve')">通过</el-button>
        <el-button type="danger" @click="openAction('reject')">驳回</el-button>
      </div>
      <el-alert v-else-if="current?.status === 'SUBMITTED'" type="warning" show-icon :closable="false"
                title="当前账号没有审批权限" style="margin-top: 12px" />
    </div>
  </el-drawer>

  <el-dialog v-model="actionVisible" :title="actionMode === 'approve' ? '通过审核' : '驳回任务'" width="520px">
    <el-alert type="info" :closable="false" show-icon
              :title="actionMode === 'approve'
                ? '通过后任务进入 APPROVED 终态，不可撤销，也不能再修改标注。'
                : '驳回后任务回到 REJECTED，质检员可按意见继续修改并重新提交。'" />
    <el-input v-model="actionComment" type="textarea" :rows="4" maxlength="500" show-word-limit
              style="margin-top: 14px"
              :placeholder="actionMode === 'approve' ? '审核意见（可选）' : '请填写驳回原因（必填）'" />
    <template #footer>
      <el-button @click="actionVisible = false">取消</el-button>
      <el-button :type="actionMode === 'approve' ? 'success' : 'danger'" :loading="acting"
                 @click="submitAction">
        {{ actionMode === 'approve' ? '确认通过' : '确认驳回' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import {
  approveTask, rejectTask, listTasks, getTask, listTaskAnnotations, listTaskHistory,
  listDefectCategories
} from '../api/task'
import { getImage, imageFileUrl } from '../api/image'
import { listBatches } from '../api/batch'

const auth = useAuthStore()
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const status = ref('SUBMITTED')
const batchId = ref(null)
const batches = ref([])
const autoRefresh = ref(true)
const statusOptions = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'REJECTED', 'APPROVED']

const detailVisible = ref(false)
const detailLoading = ref(false)
const current = ref(null)
const image = ref(null)
const annotations = ref([])
const history = ref([])
const categories = ref([])

const actionVisible = ref(false)
const actionMode = ref('approve')
const actionComment = ref('')
const acting = ref(false)

const canReview = computed(() => auth.has('REVIEWER') && current.value?.status === 'SUBMITTED')

const categoryMap = computed(() => Object.fromEntries(categories.value.map((c) => [c.id, c.name])))
function categoryName(id) { return categoryMap.value[id] || `类别#${id}` }

function statusType(s) {
  return { NEW: 'info', ASSIGNED: 'warning', IN_PROGRESS: 'primary', SUBMITTED: 'warning', REJECTED: 'danger', APPROVED: 'success' }[s] || 'info'
}
function historyType(a) {
  return { APPROVE: 'success', REJECT: 'danger', SUBMIT: 'warning', ASSIGN: 'primary', START: 'info' }[a] || 'info'
}
function actionText(a) {
  return { ASSIGN: '分配任务', START: '开始/继续处理', SUBMIT: '提交审核', APPROVE: '审核通过', REJECT: '审核驳回' }[a] || a
}
function formatTime(t) { return (t || '').replace('T', ' ').slice(0, 19) }

function boxStyle(a) {
  return {
    left: `${Number(a.xNorm) * 100}%`,
    top: `${Number(a.yNorm) * 100}%`,
    width: `${Number(a.wNorm) * 100}%`,
    height: `${Number(a.hNorm) * 100}%`
  }
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

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  current.value = row
  image.value = null
  annotations.value = []
  history.value = []
  try {
    current.value = await getTask(row.id)
    const [img, anns, his] = await Promise.all([
      getImage(current.value.imageId),
      listTaskAnnotations(current.value.id),
      listTaskHistory(current.value.id)
    ])
    image.value = img
    annotations.value = anns
    history.value = his
  } finally {
    detailLoading.value = false
  }
}

function openAction(mode) {
  actionMode.value = mode
  actionComment.value = ''
  actionVisible.value = true
}

async function submitAction() {
  if (actionMode.value === 'reject' && !actionComment.value.trim()) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  acting.value = true
  try {
    const fn = actionMode.value === 'approve' ? approveTask : rejectTask
    await fn(current.value.id, { version: current.value.version, comment: actionComment.value })
    ElMessage.success(actionMode.value === 'approve' ? '已通过审核' : '已驳回')
    actionVisible.value = false
    detailVisible.value = false
    await load()
  } catch (e) {
    // 409：版本被其他审核员抢先更新，刷新详情与列表拿到最新状态
    if (e?.response?.status === 409) {
      await openDetail({ id: current.value.id })
      await load()
    }
  } finally {
    acting.value = false
  }
}

let timer = null
onMounted(async () => {
  categories.value = await listDefectCategories()
  await loadBatches()
  await load()
  timer = setInterval(() => {
    if (autoRefresh.value && !detailVisible.value && !actionVisible.value) load()
  }, 30_000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; }
.detail-head { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.meta { color: #909399; font-size: 13px; }
.preview-wrap { background: #202225; border-radius: 6px; padding: 12px; display: flex; justify-content: center; }
.image-box { position: relative; display: inline-block; max-width: 100%; }
.image-box img { display: block; max-width: 100%; max-height: 58vh; width: auto; height: auto; }
.defect-box {
  position: absolute; border: 2px solid #e74c3c; background: rgba(231, 76, 60, 0.12);
  box-sizing: border-box; pointer-events: none;
}
.defect-label {
  position: absolute; left: 0; top: -22px; background: #e74c3c; color: #fff;
  font-size: 11px; line-height: 18px; padding: 0 5px; border-radius: 3px; white-space: nowrap;
}
.note-list { margin-top: 12px; }
.note-item { font-size: 13px; padding: 4px 0; border-bottom: 1px dashed #ebeef5; }
.history-line { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.history-comment { color: #606266; font-size: 13px; margin-top: 4px; }
.actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 20px; }
</style>
