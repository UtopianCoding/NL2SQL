import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token'),
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null')
  }),
  
  getters: {
    isLoggedIn: (state) => !!state.token,
    userId: (state) => state.userInfo?.id
  },
  
  actions: {
    async login(data) {
      const res = await authApi.login(data)
      this.token = res.accessToken
      this.userInfo = res.userInfo
      localStorage.setItem('token', res.accessToken)
      localStorage.setItem('userInfo', JSON.stringify(res.userInfo))
      return res
    },
    
    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.token = null
        this.userInfo = null
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
      }
    },
    
    async fetchUserInfo() {
      const userInfo = await authApi.getCurrentUser()
      this.userInfo = userInfo
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    }
  }
})
