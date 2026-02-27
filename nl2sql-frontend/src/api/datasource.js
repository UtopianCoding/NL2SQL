import request from '@/utils/request'

export const datasourceApi = {
  getList() {
    return request.get('/datasource/list')
  },
  
  getById(id) {
    return request.get(`/datasource/${id}`)
  },
  
  create(data) {
    return request.post('/datasource/add', data)
  },
  
  update(id, data) {
    return request.put(`/datasource/update/${id}`, data)
  },
  
  delete(id) {
    return request.delete(`/datasource/delete/${id}`)
  },
  
  testConnection(data) {
    return request.post('/datasource/test', data)
  },
  
  getTables(id) {
    return request.get(`/datasource/${id}/tables`, { timeout: 60000 })
  },
  
  getSyncedTableNames(id) {
    return request.get(`/datasource/${id}/synced-tables`)
  },
  
  syncTables(id, tableNames) {
    return request.post(`/datasource/${id}/sync`, tableNames, { timeout: 60000 })
  },

  getSyncProgress(taskId) {
    return request.get(`/datasource/sync/progress/${taskId}`)
  }
}
