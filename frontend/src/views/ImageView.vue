<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <el-select v-model="batchId" placeholder="选择生产批次" style="width: 240px" @change="load">
          <el-option v-for="b in batches" :key="b.id" :label="`${b.batchCode} / ${b.productType}`" :value="b.id" />
        </el-select>
        <el-button @click="load">刷新</el-button>
        <span class="hint">单张 ≤10MB，支持 JPG/PNG，同一批次重复文件自动去重</span>
      </div>
    </template>

    <el-empty v-if="!batchId" description="请先选择生产批次" />

    <template v-else>
      <div class="upload-bar">
        <el-upload
          v-model:file-list="fileList"
          :auto-upload="false"
          multiple
          accept="image/jpeg,image/png"
          :limit="20"
          :on-exceed="onExceed"
          drag
          style="flex: 1"
        >
          <div class="el-upload__text">拖拽图片到此处，或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">仅 jpg / png，单张最大 10MB</div>
          </template>
        </el-upload>
        <el-button type="primary" :loading="uploading" :disabled="!fileList.length" @click="doUpload">
          上传（{{ fileList.length }}）
        </el-button>
      </div>

      <el-alert v-if="uploadSummary" :title="uploadSummary" :type="uploadError ? 'warning' : 'success'"
                show-icon :closable="false" style="margin: 12px 0" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="缩略图" width="110">
          <template #default="{ row }">
            <el-image :src="imageFileUrl(row.id)" fit="cover" style="width: 72px; height: 54px"
                      :preview-src-list="[imageFileUrl(row.id)]" preview-teleported />
          </template>
        </el-table-column>
        <el-table-column prop="originalName" label="文件名" show-overflow-tooltip />
        <el-table-column label="尺寸" width="130">
          <template #default="{ row }">{{ row.width }} × {{ row.height }}</template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ (row.sizeBytes / 1024).toFixed(1) }} KB</template>
        </el-table-column>
        <el-table-column prop="sha256" label="SHA-256" width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary"
                       @click="previewId = row.id; previewVisible = true">查看</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 16px" background layout="total, prev, pager, next"
                     :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
    </template>

    <el-dialog v-model="previewVisible" title="图片预览" width="70%">
      <el-image v-if="previewId" :src="imageFileUrl(previewId)" fit="contain" style="width: 100%" />
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listBatches } from '../api/batch'
import { uploadImages, listImages, deleteImage, imageFileUrl } from '../api/image'

const batches = ref([])
const batchId = ref(null)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const uploading = ref(false)
const fileList = ref([])
const uploadSummary = ref('')
const uploadError = ref(false)
const previewVisible = ref(false)
const previewId = ref(null)

function onExceed() {
  ElMessage.warning('单次最多选择 20 张图片')
}

async function load() {
  if (!batchId.value) return
  loading.value = true
  try {
    const res = await listImages({ batchId: batchId.value, page: page.value - 1, size })
    rows.value = res.content
    total.value = res.totalElements
  } finally {
    loading.value = false
  }
}

async function doUpload() {
  const files = fileList.value.map((f) => f.raw).filter(Boolean)
  if (!files.length) return
  uploading.value = true
  uploadError.value = false
  try {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    const results = await uploadImages(batchId.value, form)
    const ok = results.filter((r) => r.success).length
    const dup = results.filter((r) => r.duplicate).length
    const failed = results.filter((r) => !r.success)
    uploadError.value = failed.length > 0
    let msg = `成功 ${ok - dup} 张，重复 ${dup} 张`
    if (failed.length) msg += `，失败 ${failed.length} 张：` + failed.map((f) => `${f.originalName}(${f.message})`).join('；')
    uploadSummary.value = msg
    if (!failed.length) ElMessage.success('上传完成')
    fileList.value = []
    page.value = 1
    await load()
  } finally {
    uploading.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除图片「${row.originalName}」？文件将保留用于追溯。`, '确认删除')
  await deleteImage(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  const res = await listBatches({ page: 0, size: 100 })
  batches.value = res.content
  if (batches.value.length) {
    batchId.value = batches.value[0].id
    await load()
  }
})
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; }
.hint { color: #909399; font-size: 12px; }
.upload-bar { display: flex; gap: 16px; align-items: flex-start; margin-bottom: 8px; }
</style>
