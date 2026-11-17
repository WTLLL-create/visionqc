<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="名称/编码搜索" style="width: 240px" clearable
                  @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="openCreate">新建设备</el-button>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="deviceName" label="设备名称" />
      <el-table-column prop="deviceCode" label="设备编码" width="140" />
      <el-table-column prop="deviceStatus" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.deviceStatus === 'ENABLED' ? 'success' : 'danger'">
            {{ row.deviceStatus === 'ENABLED' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.deviceStatus === 'ENABLED' ? 'warning' : 'success'"
                     @click="toggle(row)">
            {{ row.deviceStatus === 'ENABLED' ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px" background layout="total, prev, pager, next"
                   :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑设备' : '新建设备'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="form.deviceName" />
        </el-form-item>
        <el-form-item label="设备编码" prop="deviceCode">
          <el-input v-model="form.deviceCode" :disabled="!!editing" placeholder="字母/数字/下划线/中划线" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDevices, createDevice, updateDevice, changeDeviceStatus, deleteDevice } from '../api/device'

const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const page = ref(1)
const size = 20
const total = ref(0)
const dialogVisible = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({ deviceName: '', deviceCode: '', description: '' })
const rules = {
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceCode: [{ required: true, message: '请输入设备编码', trigger: 'blur' },
               { pattern: /^[A-Za-z0-9_-]{2,50}$/, message: '仅字母/数字/下划线/中划线', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await listDevices({ keyword: keyword.value, page: page.value - 1, size })
    rows.value = res.content
    total.value = res.totalElements
  } finally { loading.value = false }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { deviceName: '', deviceCode: '', description: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, { deviceName: row.deviceName, deviceCode: row.deviceCode, description: row.description })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (editing.value) {
      await updateDevice(editing.value.id, form)
      ElMessage.success('已更新')
    } else {
      await createDevice(form)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } finally { saving.value = false }
}

async function toggle(row) {
  await changeDeviceStatus(row.id, row.deviceStatus !== 'ENABLED')
  ElMessage.success('状态已更新')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除设备「${row.deviceName}」？历史数据将保留引用。`, '确认删除')
  await deleteDevice(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; }
</style>
