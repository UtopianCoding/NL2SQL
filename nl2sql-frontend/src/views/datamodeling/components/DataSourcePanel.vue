<template>
  <div class="datasource-panel">
    <div class="panel-header">
      <h4>数据源</h4>
    </div>
    <div class="panel-list" v-loading="loading">
      <div
        v-for="ds in dsList"
        :key="ds.id"
        class="ds-item"
        :class="{ active: selectedId === ds.id }"
        @click="handleSelect(ds)"
      >
        <img :src="getDbIcon(ds.type)" :alt="ds.type" class="ds-icon" />
        <div class="ds-info">
          <div class="ds-name">{{ ds.name }}</div>
          <div class="ds-meta">{{ ds.databaseName }}</div>
        </div>
        <el-tag
          :type="ds.status === 1 ? 'success' : 'info'"
          size="small"
          effect="plain"
        >
          {{ ds.status === 1 ? '在线' : '离线' }}
        </el-tag>
      </div>
      <el-empty v-if="!loading && dsList.length === 0" description="暂无数据源" :image-size="60" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useDataSourceStore } from '@/stores/datasource'

const props = defineProps({
  selectedId: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['select'])

const datasourceStore = useDataSourceStore()
const loading = ref(false)
const dsList = ref([])

const getDbIcon = (type) => {
  const icons = {
    mysql: '/icons/mysql.png',
    postgresql: '/icons/postgresql.png',
    oracle: '/icons/oracle.png',
    sqlserver: '/icons/sqlserver.png',
    gaussdb: '/icons/gaussdb.png',
    kingbase: '/icons/kingbase.png',
    oceanbase: '/icons/oceanbase.png',
    dm: '/icons/dm.png'
  }
  return icons[(type || '').toLowerCase()] || '/icons/database.svg'
}

const handleSelect = (ds) => {
  emit('select', ds.id)
}

onMounted(async () => {
  loading.value = true
  try {
    await datasourceStore.fetchList()
    dsList.value = datasourceStore.list || []
  } finally {
    loading.value = false
  }
})
</script>

<style lang="less" scoped>
.datasource-panel {
  width: 220px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .panel-header {
    padding: 16px;
    border-bottom: 1px solid var(--border-color);

    h4 {
      margin: 0;
      font-size: 14px;
      font-weight: 600;
      color: var(--text-primary);
    }
  }

  .panel-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px;

    .ds-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;
      margin-bottom: 4px;

      &:hover {
        background: #f5f7fa;
      }

      &.active {
        background: #ecf5ff;
        border: 1px solid #b3d8ff;

        .ds-name {
          color: #409eff;
          font-weight: 600;
        }
      }

      .ds-icon {
        width: 28px;
        height: 28px;
        object-fit: contain;
        flex-shrink: 0;
      }

      .ds-info {
        flex: 1;
        min-width: 0;

        .ds-name {
          font-size: 13px;
          color: var(--text-primary);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .ds-meta {
          font-size: 11px;
          color: var(--text-secondary);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          margin-top: 2px;
        }
      }
    }
  }
}
</style>
