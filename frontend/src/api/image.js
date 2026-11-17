import request from './request'

// 图片：上传(ADMIN)/列表/详情/二进制/删除
export const uploadImages = (batchId, formData) =>
  request.post(`/images/upload/${batchId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

export const listImages = (params) => request.get('/images', { params })
export const getImage = (id) => request.get(`/images/${id}`)
export const deleteImage = (id) => request.delete(`/images/${id}`)
// 图片二进制地址（浏览器直接访问，同源自动带 Cookie）
export const imageFileUrl = (id) => `/api/v1/images/${id}/file`
