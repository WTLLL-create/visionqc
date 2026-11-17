import request from './request'

export const listTasks = (params) => request.get('/tasks', { params })
export const getTask = (id) => request.get(`/tasks/${id}`)
export const listTaskAnnotations = (taskId) => request.get(`/tasks/${taskId}/annotations`)
export const saveTaskAnnotations = (taskId, annotations) =>
  request.put(`/tasks/${taskId}/annotations`, { annotations })
export const listTaskHistory = (taskId) => request.get(`/tasks/${taskId}/history`)
export const listDefectCategories = () => request.get('/defect-categories')

export const assignTask = (taskId, data) => request.post(`/tasks/${taskId}/assign`, data)
export const startTask = (taskId, data) => request.post(`/tasks/${taskId}/start`, data)
export const submitTask = (taskId, data) => request.post(`/tasks/${taskId}/submit`, data)
export const approveTask = (taskId, data) => request.post(`/tasks/${taskId}/approve`, data)
export const rejectTask = (taskId, data) => request.post(`/tasks/${taskId}/reject`, data)

export const listUsers = (params) => request.get('/users', { params })
