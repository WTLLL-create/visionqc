// 聚合出口：页面统一从各领域模块导入
export * from './auth'
export * from './device'
export * from './batch'
export * from './image'
export * from './task'

import request from './request'
// 统计接口（Phase 4 实现，此处保持调用入口）
export const getStatsOverview = () => request.get('/stats/overview')
export const getDefectDistribution = () => request.get('/stats/defect-distribution')
export const getDefectTrend = (days) => request.get('/stats/defect-trend', { params: { days } })
export const listMyTasks = (params) => request.get('/tasks', { params })
export const listPendingReview = (params) => request.get('/tasks', { params, status: 'SUBMITTED' })
