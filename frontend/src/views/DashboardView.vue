<template>
  <el-row :gutter="20">
    <el-col :span="6" v-for="card in cards" :key="card.title">
      <el-card>
        <div class="num">{{ card.value }}</div>
        <div class="label">{{ card.title }}</div>
      </el-card>
    </el-col>
  </el-row>
  <el-card style="margin-top: 20px">
    <template #header>项目进展（Phase 0 骨架）</template>
    <el-alert type="info" :closable="false"
      title="当前为 Phase 0 骨架版：登录认证、设备管理、批次管理已可联调；图片上传/标注/审核/看板将在后续阶段实现（见 docs/06-阶段开发计划.md）" />
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getStatsOverview } from '../api'

const cards = ref([
  { title: '图片总数', value: '-' },
  { title: '已审核', value: '-' },
  { title: '待审核', value: '-' },
  { title: '缺陷图片数', value: '-' }
])

onMounted(async () => {
  try {
    const s = await getStatsOverview()
    cards.value[0].value = s.totalImages ?? '-'
    cards.value[1].value = s.approved ?? '-'
    cards.value[2].value = s.pendingReview ?? '-'
    cards.value[3].value = s.defectImages ?? '-'
  } catch { /* 统计接口 Phase 4 实现，失败保持占位 */ }
})
</script>

<style scoped>
.num { font-size: 28px; font-weight: bold; color: #409eff; }
.label { color: #909399; margin-top: 4px; }
</style>
