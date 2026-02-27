<template>
  <div class="datasource-page">
    <!-- 顶部搜索和操作栏 -->
    <div class="page-header">
      <h2 class="page-title">数据源管理</h2>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索数据源"
        :prefix-icon="Search"
        style="width: 300px"
        clearable
      />
    </div>

    <div class="page-content">
      <!-- 左侧数据源列表 -->
      <div class="datasource-list">
        <!-- 筛选标签 -->
        <div class="filter-tabs">
          <el-button
            v-for="tab in filterTabs"
            :key="tab.value"
            :type="activeFilter === tab.value ? 'primary' : 'default'"
            :plain="activeFilter !== tab.value"
            size="small"
            @click="activeFilter = tab.value"
          >
            {{ tab.label }}
          </el-button>
        </div>

        <!-- 数据源卡片 -->
        <div class="card-grid" v-loading="loading">
          <div
            v-for="ds in filteredList"
            :key="ds.id"
            class="ds-card"
            @click="handleSelectDs(ds)"
          >
            <div class="card-icon">
              <img :src="getDbIcon(ds.type)" :alt="ds.type" />
            </div>
            <div class="card-content">
              <div class="card-header">
                <div class="card-title">{{ ds.name }}</div>
                <div class="card-actions" @click.stop>
                  <span class="action-btn edit" @click="handleEdit(ds)" title="编辑">
                    <el-icon><Edit /></el-icon>
                  </span>
                  <span class="action-btn delete" @click="handleDelete(ds)" title="删除">
                    <el-icon><Delete /></el-icon>
                  </span>
                </div>
              </div>
              <div class="card-meta">
                <el-tag :type="ds.status === 1 ? 'success' : 'danger'" size="small">
                  {{ ds.status === 1 ? '在线' : '离线' }}
                </el-tag>
                <span class="db-type">{{ ds.type }}</span>
              </div>
              <div class="card-info">
                <p>数据库: {{ ds.databaseName }}</p>
                <p>服务器: {{ ds.host }}:{{ ds.port }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧操作面板 -->
      <div class="action-panel">
        <!-- 新增数据源按钮 -->
        <el-button type="primary" size="large" class="add-btn" @click="showAddDialog = true">
          <el-icon><Plus /></el-icon>
          新增数据源
        </el-button>

        <!-- 快速连接 -->
        <div class="quick-connect">
          <h4>快速连接</h4>
          <div class="quick-items">
            <div
              v-for="db in quickDatabases"
              :key="db.type"
              class="quick-item"
              @click="handleQuickConnect(db.type)"
            >
              <img :src="db.icon" :alt="db.type" />
              <span>{{ db.label }}</span>
            </div>
          </div>
        </div>

        <!-- 连接统计 -->
        <div class="stats-panel">
          <h4>连接统计</h4>
          <div class="stat-item">
            <span>总连接数</span>
            <span class="stat-value">{{ datasourceStore.list.length }}</span>
          </div>
          <div class="stat-item">
            <span>在线</span>
            <span class="stat-value success">{{ onlineCount }}</span>
          </div>
          <div class="stat-item">
            <span>离线</span>
            <span class="stat-value danger">{{ offlineCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增数据源对话框 -->
    <AddDataSourceDialog
      v-model="showAddDialog"
      @success="handleAddSuccess"
    />

    <!-- 编辑数据源对话框 -->
    <EditDataSourceDialog
      v-model="showEditDialog"
      :datasource="editingDs"
      @success="handleEditSuccess"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDataSourceStore } from '@/stores/datasource'
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { datasourceApi } from '@/api/datasource'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import AddDataSourceDialog from './components/AddDataSource/index.vue'
import EditDataSourceDialog from './components/EditDataSource/index.vue'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const datasourceStore = useDataSourceStore()

const searchKeyword = ref('')
const activeFilter = ref('all')
const loading = ref(false)
const showAddDialog = ref(false)
const showEditDialog = ref(false)
const editingDs = ref(null)

const filterTabs = [
  { label: '全部', value: 'all' },
  { label: 'MySQL', value: 'mysql' },
  { label: 'PostgreSQL', value: 'postgresql' },
  { label: 'Oracle', value: 'oracle' }
]

const quickDatabases = [
  { type: 'mysql', label: 'MySQL', icon: '/icons/mysql.png' },
  { type: 'sqlserver', label: 'SQL Server', icon: '/icons/sqlserver.png' },
  { type: 'oracle', label: 'Oracle', icon: '/icons/oracle.png' },
  { type: 'gaussdb', label: 'GaussDB', icon: '/icons/gaussdb.png' }
]

const filteredList = computed(() => {
  let list = datasourceStore.list || []

  if (activeFilter.value !== 'all') {
    list = list.filter(ds => ds.type?.toLowerCase() === activeFilter.value)
  }

  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    list = list.filter(ds =>
      (ds.name?.toLowerCase() || '').includes(keyword) ||
      (ds.host?.toLowerCase() || '').includes(keyword) ||
      (ds.databaseName?.toLowerCase() || '').includes(keyword)
    )
  }

  return list
})

const onlineCount = computed(() =>
  datasourceStore.list.filter(ds => ds.status === 1).length
)

const offlineCount = computed(() =>
  datasourceStore.list.filter(ds => ds.status !== 1).length
)

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
  return icons[type.toLowerCase()] || '/icons/database.svg'
}

const formatTime = (time) => {
  return dayjs(time).fromNow()
}

const handleSelectDs = (ds) => {
  datasourceStore.setCurrent(ds)
}

const handleQuickConnect = (_type) => {
  showAddDialog.value = true
  // TODO: 预设数据库类型
}

const handleAddSuccess = () => {
  datasourceStore.fetchList()
}

const handleEdit = (ds) => {
  editingDs.value = ds
  showEditDialog.value = true
}

const handleEditSuccess = () => {
  datasourceStore.fetchList()
}

const handleDelete = async (ds) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除数据源 "${ds.name}" 吗？删除后相关的表结构信息也会被清除。`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await datasourceApi.delete(ds.id)
    ElMessage.success('删除成功')
    datasourceStore.fetchList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await datasourceStore.fetchList()
  } finally {
    loading.value = false
  }
})
</script>

<style lang="less" scoped>
.datasource-page {
  height: 100%;
  display: flex;
  flex-direction: column;

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .page-title {
      font-size: 20px;
      font-weight: 600;
      margin: 0;
    }
  }

  .page-content {
    flex: 1;
    display: flex;
    gap: 20px;
    overflow: hidden;

    .datasource-list {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;

      .filter-tabs {
        display: flex;
        gap: 10px;
        margin-bottom: 20px;
        flex-wrap: wrap;
      }

      .card-grid {
        flex: 1;
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
        gap: 16px;
        overflow-y: auto;
        padding-right: 10px;
        align-content: start;

        .ds-card {
          background: #fff;
          border-radius: 12px;
          padding: 20px;
          display: flex;
          gap: 16px;
          cursor: pointer;
          border: 1px solid var(--border-color);
          transition: all 0.3s;
          height: fit-content;

          &:hover {
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
            border-color: var(--primary-color);
          }

          .card-icon {
            width: 48px;
            height: 48px;

            img {
              width: 100%;
              height: 100%;
              object-fit: contain;
            }
          }

          .card-content {
            flex: 1;

            .card-header {
              display: flex;
              justify-content: space-between;
              align-items: flex-start;
              margin-bottom: 8px;

              .card-title {
                font-size: 16px;
                font-weight: 600;
                flex: 1;
              }

              .card-actions {
                display: flex;
                gap: 8px;

                .action-btn {
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  width: 28px;
                  height: 28px;
                  border-radius: 6px;
                  cursor: pointer;
                  transition: all 0.2s;

                  &.edit {
                    color: #409eff;
                    background: rgba(64, 158, 255, 0.1);

                    &:hover {
                      background: rgba(64, 158, 255, 0.2);
                    }
                  }

                  &.delete {
                    color: #f56c6c;
                    background: rgba(245, 108, 108, 0.1);

                    &:hover {
                      background: rgba(245, 108, 108, 0.2);
                    }
                  }
                }
              }
            }

            .card-meta {
              display: flex;
              align-items: center;
              gap: 10px;
              margin-bottom: 10px;

              .db-type {
                color: var(--text-secondary);
                font-size: 12px;
              }
            }

            .card-info {
              font-size: 12px;
              color: var(--text-secondary);

              p {
                margin: 4px 0;
              }
            }
          }
        }
      }
    }

    .action-panel {
      width: 280px;
      display: flex;
      flex-direction: column;
      gap: 20px;

      .add-btn {
        width: 100%;
        height: 48px;
        font-size: 16px;
      }

      .quick-connect,
      .stats-panel {
        background: #fff;
        border-radius: 12px;
        padding: 20px;

        h4 {
          margin: 0 0 16px 0;
          font-size: 14px;
          color: var(--text-primary);
        }
      }

      .quick-connect {
        .quick-items {
          display: grid;
          grid-template-columns: repeat(2, 1fr);
          gap: 12px;

          .quick-item {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
            padding: 16px;
            border: 1px solid var(--border-color);
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.3s;

            &:hover {
              border-color: var(--primary-color);
              background: var(--primary-light);
            }

            img {
              width: 32px;
              height: 32px;
            }

            span {
              font-size: 12px;
              color: var(--text-regular);
            }
          }
        }
      }

      .stats-panel {
        .stat-item {
          display: flex;
          justify-content: space-between;
          padding: 10px 0;
          border-bottom: 1px solid var(--border-color);

          &:last-child {
            border-bottom: none;
          }

          .stat-value {
            font-weight: 600;

            &.success {
              color: var(--success-color);
            }

            &.danger {
              color: var(--danger-color);
            }
          }
        }
      }
    }
  }
}
</style>
