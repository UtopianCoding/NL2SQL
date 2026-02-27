import { defineStore } from 'pinia'
import { datasourceApi } from '@/api/datasource'

export const useDataSourceStore = defineStore('datasource', {
  state: () => ({
    list: [],
    current: null,
    loading: false
  }),
  
  actions: {
    async fetchList() {
      this.loading = true
      try {
        this.list = await datasourceApi.getList()
      } finally {
        this.loading = false
      }
    },
    
    async create(data) {
      const ds = await datasourceApi.create(data)
      this.list.push(ds)
      return ds
    },
    
    async update(id, data) {
      const ds = await datasourceApi.update(id, data)
      const index = this.list.findIndex(item => item.id === id)
      if (index > -1) {
        this.list[index] = ds
      }
      return ds
    },
    
    async remove(id) {
      await datasourceApi.delete(id)
      this.list = this.list.filter(item => item.id !== id)
    },
    
    setCurrent(ds) {
      this.current = ds
    }
  }
})
