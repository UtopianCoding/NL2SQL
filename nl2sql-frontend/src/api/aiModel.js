import request from '@/utils/request'

export const aiModelApi = {
  getList(keyword) {
    return request.get('/ai-model/list', { params: { keyword } })
  },

  getById(id) {
    return request.get(`/ai-model/${id}`)
  },

  create(data) {
    return request.post('/ai-model', data)
  },

  update(id, data) {
    return request.put(`/ai-model/${id}`, data)
  },

  delete(id) {
    return request.delete(`/ai-model/${id}`)
  },

  setDefault(id) {
    return request.put(`/ai-model/${id}/default`)
  },

  getDefault() {
    return request.get('/ai-model/default')
  }
}
