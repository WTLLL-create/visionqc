import request from './request'

export const listBatches = (params) => request.get('/batches', { params })
export const getBatch = (id) => request.get(`/batches/${id}`)
export const createBatch = (data) => request.post('/batches', data)
export const closeBatch = (id) => request.post(`/batches/${id}/close`)
