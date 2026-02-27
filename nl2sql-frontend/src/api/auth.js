import request from '@/utils/request'

export const authApi = {
  login(data) {
    return request.post('/auth/login', data)
  },
  
  logout() {
    return request.post('/auth/logout')
  },
  
  getCurrentUser() {
    return request.get('/auth/current')
  }
}
