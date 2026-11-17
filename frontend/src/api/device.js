import request from './request'

export const listDevices = (params) => request.get('/devices', { params })
export const getDevice = (id) => request.get(`/devices/${id}`)
export const createDevice = (data) => request.post('/devices', data)
export const updateDevice = (id, data) => request.put(`/devices/${id}`, data)
export const changeDeviceStatus = (id, enable) =>
  request.patch(`/devices/${id}/status?enable=${enable}`)
export const deleteDevice = (id) => request.delete(`/devices/${id}`)
