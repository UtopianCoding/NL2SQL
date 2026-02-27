import request from '@/utils/request'

export const chatApi = {
  query(data) {
    return request.post('/nl2sql/query', data)
  },
  
  createConversation(dsId) {
    return request.post('/conversation/create', null, { params: { dsId } })
  },
  
  getConversationList(page = 1, size = 20) {
    return request.get('/conversation/list', { params: { page, size } })
  },
  
  getConversation(id) {
    return request.get(`/conversation/${id}`)
  },
  
  deleteConversation(id) {
    return request.delete(`/conversation/${id}`)
  },
  
  getHistoryList(page = 1, size = 20) {
    return request.get('/history/list', { params: { page, size } })
  },
  
  toggleFavorite(id) {
    return request.post(`/history/${id}/favorite`)
  },
  
  getFavorites() {
    return request.get('/history/favorites')
  }
}
