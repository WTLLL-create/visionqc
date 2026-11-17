<template>
  <el-card>
    <template #header>质量统计看板</template>
    <el-alert type="info" :closable="false"
      title="Phase 4 实现：ECharts 缺陷分布/每日趋势/设备质量/批次不良率，全部取自真实统计 API（验收场景 F）" />
    <div ref="chartEl" style="height: 360px; margin-top: 16px" v-show="false" />
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
// ECharts 已在依赖中，图表渲染与指标口径（docs/03 第 5 节）在 Phase 4 接入
const chartEl = ref()
</script>
