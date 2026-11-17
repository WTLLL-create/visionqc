<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <el-select v-model="status" style="width: 160px" @change="load">
          <el-option label="已通过（APPROVED）" value="APPROVED" />
          <el-option label="已驳回（REJECTED）" value="REJECTED" />
          <el-option label="全部历史" value="" />
        </el-select>
        <el-select v-model="batchId" placeholder="全部批次" clearable filterable style="width: 230px" @change="load">
          <el-option v-for="b in batches" :key="b.id" :label="`${b.batchCode} / ${b.productType}`" :value="b.id" />
        </el-select>
        <el-button @click="load">刷新</el-button>
        <span class="hint">每次状态流转都落库 review_records，按时间顺序展示</span>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" border stripe @row-click="openDetail">
      <el-table-column prop="id" label="任务ID" width="90" />
      <el-table-column prop="imageId" label="图片ID" width="90" />
      <el-table-column label="最终状态" width="130">
        <template #default="{ row }">
          <el-tag :type="row.status === 'APPROVED' ? 'success' : 'danger'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column prop="reviewerId" label="审核员ID" width="110" />
      <el-table-column prop="updatedAt" label="最后更新时间" width="180" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openDetail(row)">查看时间线</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px" background layout="total, prev, pager, next"
                   :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
  </el-card>

  <el-drawer v-model="visible" size="60%" :title="`任务 #${current?.id || ''} 审核时间线`">
    <div v-loading="detailLoading">
      <div class="detail-head" v-if="current">
        <el-tag :type="current.status === 'APPROVED' ? 'success' : 'danger'" size="large">{{ current.status }}</el-tag>
        <span class="meta">版本 {{ current.version }}</span>
        <span v-if="image" class="meta">{{ image.width }} × {{ image.height }}</span>
        <span class="meta">标注 {{ annotations.length }} 个</span>
      </div>

      <el-timeline>
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
      <el-empty v-if="!history.length" description="暂无审核记录" :image-size="60" />
    </div>
  </el-drawer>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listTasks, getTask, listTaskAnnotations, listTaskHistory } from '../api/task'
import { getImage } from '../api/image'
import { listBatches } from '../api/batch'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const status = ref('APPROVED')
const batchId = ref(null)
const batches = ref([])

const visible = ref(false)
const detailLoading = ref(false)
const current = ref(null)
const image = ref(null)
const annotations = ref([])
const history = ref([])

function historyType(a) {
  return { APPROVE: 'success', REJECT: 'danger', SUBMIT: 'warning', ASSIGN: 'primary', START: 'info' }[a] || 'info'
}
function actionText(a) {
  return { ASSIGN: '分配任务', START: '开始/继续处理', SUBMIT: '提交审核', APPROVE: '审核通过', REJECT: '审核驳回' }[a] || a
}
function formatTime(t) { return (t || '').replace('T', ' ').slice(0, 19) }

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
  visible.value = true
  detailLoading.value = true
  current.value = row
  history.value = []
  annotations.value = []
  image.value = null
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

onMounted(async () => {
  await loadBatches()
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; }
.detail-head { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; }
.meta { color: #909399; font-size: 13px; }
.history-line { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.history-comment { color: #606266; font-size: 13px; margin-top: 4px; }
</style>
