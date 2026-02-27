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

export const userApi = {
  // 更新用户信息
  updateProfile(data) {
    return request.put('/user/profile', data)
  },
  
  // 修改密码
  changePassword(data) {
    return request.put('/user/password', data)
  }
}
