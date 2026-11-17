<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <el-select v-model="deviceId" placeholder="按设备筛选" clearable style="width: 200px">
          <el-option v-for="d in devices" :key="d.id" :label="d.deviceName" :value="d.id" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="dialogVisible = true">新建批次</el-button>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="batchCode" label="批次编码" width="140" />
      <el-table-column prop="deviceName" label="所属设备" />
      <el-table-column prop="productType" label="产品型号" />
      <el-table-column prop="captureDate" label="拍摄日期" width="120" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'OPEN' ? 'success' : 'info'">
            {{ row.status === 'OPEN' ? '开放' : '已关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="row.status === 'OPEN'" link type="warning" @click="close(row)">关闭批次</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px" background layout="total, prev, pager, next"
                   :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />

    <el-dialog v-model="dialogVisible" title="新建生产批次" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="批次编码" prop="batchCode">
          <el-input v-model="form.batchCode" placeholder="如 B20261117-01" />
        </el-form-item>
        <el-form-item label="所属设备" prop="deviceId">
          <el-select v-model="form.deviceId" style="width: 100%">
            <el-option v-for="d in devices" :key="d.id" :label="d.deviceName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品型号" prop="productType">
          <el-input v-model="form.productType" />
        </el-form-item>
        <el-form-item label="拍摄日期" prop="captureDate">
          <el-date-picker v-model="form.captureDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listBatches, createBatch, closeBatch } from '../api/batch'
import { listDevices } from '../api/device'

const rows = ref([])
const devices = ref([])
const loading = ref(false)
const saving = ref(false)
const deviceId = ref(null)
const page = ref(1)
const size = 20
const total = ref(0)
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ batchCode: '', deviceId: null, productType: '', captureDate: '' })
const rules = {
  batchCode: [{ required: true, message: '请输入批次编码', trigger: 'blur' },
              { pattern: /^[A-Za-z0-9_-]{2,50}$/, message: '仅字母/数字/下划线/中划线', trigger: 'blur' }],
  deviceId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  productType: [{ required: true, message: '请输入产品型号', trigger: 'blur' }],
  captureDate: [{ required: true, message: '请选择拍摄日期', trigger: 'change' }]
}

async function load() {
  loading.value = true
  try {
    const res = await listBatches({ deviceId: deviceId.value, page: page.value - 1, size })
    rows.value = res.content
    total.value = res.totalElements
  } finally { loading.value = false }
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    await createBatch(form)
    ElMessage.success('批次已创建')
    dialogVisible.value = false
    Object.assign(form, { batchCode: '', deviceId: null, productType: '', captureDate: '' })
    await load()
  } finally { saving.value = false }
}

async function close(row) {
  await closeBatch(row.id)
  ElMessage.success('批次已关闭')
  await load()
}

onMounted(async () => {
  const res = await listDevices({ page: 0, size: 100 })
  devices.value = res.content
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; }
</style>
