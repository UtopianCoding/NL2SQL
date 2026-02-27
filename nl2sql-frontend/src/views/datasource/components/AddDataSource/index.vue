<template>
  <el-dialog
    v-model="visible"
    title="新增数据源"
    width="800px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-steps :active="currentStep" align-center class="steps">
      <el-step title="选择类型" />
      <el-step title="配置连接" />
      <el-step title="选择表" />
    </el-steps>
    
    <div class="step-content">
      <!-- Step 1: 选择数据源类型 -->
      <div v-if="currentStep === 0" class="step-type">
        <h4>选择数据源类型</h4>
        <div class="type-grid">
          <div
            v-for="db in databaseTypes"
            :key="db.value"
            class="type-item"
            :class="{ active: formData.type === db.value }"
            @click="formData.type = db.value"
          >
            <img :src="db.icon" :alt="db.label" class="db-icon" />
            <span>{{ db.label }}</span>
          </div>
        </div>
      </div>
      
      <!-- Step 2: 配置连接信息 -->
      <div v-if="currentStep === 1" class="step-config">
        <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
          <el-form-item label="连接名称" prop="name">
            <el-input v-model="formData.name" placeholder="请输入连接名称，如：生产环境数据库" />
          </el-form-item>
          <el-form-item label="IP地址" prop="host">
            <el-input v-model="formData.host" placeholder="请输入IP地址，如:192.168.1.100" />
          </el-form-item>
          <el-form-item label="端口" prop="port">
            <el-input v-model.number="formData.port" placeholder="请输入端口号，如:3306" />
          </el-form-item>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="formData.username" placeholder="请输入数据库用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="formData.password" type="password" placeholder="请输入密码" show-password />
          </el-form-item>
          <el-form-item label="数据库名称" prop="databaseName">
            <el-input v-model="formData.databaseName" placeholder="请输入数据库名称" />
          </el-form-item>
        </el-form>
        
        <el-button type="primary" @click="testConnection" :loading="testing">
          测试连接
        </el-button>
      </div>
      
      <!-- Step 3: 选择同步的表 -->
      <div v-if="currentStep === 2" class="step-tables">
        <div v-if="!syncing" class="table-header">
          <span>选择要同步的数据表</span>
          <el-checkbox v-model="selectAll" @change="handleSelectAll">全选</el-checkbox>
        </div>
        
        <div v-if="syncing" class="sync-progress">
          <p>正在同步表结构，请稍候...</p>
          <el-progress :percentage="syncProgress" :format="progressFormat" />
          <p class="current-table" v-if="currentSyncTable">当前: {{ currentSyncTable }}</p>
        </div>
        
        <el-table
          v-show="!syncing"
          ref="tableRef"
          :data="tableList"
          v-loading="loadingTables"
          max-height="400"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="50" />
          <el-table-column prop="tableName" label="表名" />
          <el-table-column prop="rowCount" label="记录数" width="120" />
          <el-table-column prop="updateTime" label="最后更新" width="180">
            <template #default="{ row }">
              {{ row.updateTime || '-' }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
    
    <template #footer>
      <el-button @click="handleCancel" :disabled="syncing">取消</el-button>
      <el-button v-if="currentStep > 0" @click="currentStep--" :disabled="syncing">上一步</el-button>
      <el-button v-if="currentStep < 2" type="primary" @click="handleNext" :disabled="!canNext">
        下一步
      </el-button>
      <el-button v-if="currentStep === 2" type="primary" @click="handleSubmit" :loading="submitting" :disabled="syncing">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { datasourceApi } from '@/api/datasource'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const formRef = ref()
const tableRef = ref()
const currentStep = ref(0)
const testing = ref(false)
const loadingTables = ref(false)
const submitting = ref(false)
const selectAll = ref(false)
const syncing = ref(false)
const syncProgress = ref(0)
const currentSyncTable = ref('')
let pollTimer = null

const formData = ref({
  name: '',
  type: 'mysql',
  host: '',
  port: 3306,
  databaseName: '',
  username: '',
  password: ''
})

const tableList = ref([])
const selectedTables = ref([])
const createdDsId = ref(null)

const databaseTypes = [
  { label: 'MySQL', value: 'mysql', icon: '/icons/mysql.png' },
  { label: 'SQL Server', value: 'sqlserver', icon: '/icons/sqlserver.png' },
  { label: 'Oracle', value: 'oracle', icon: '/icons/oracle.png' },
  { label: 'PostgreSQL', value: 'postgresql', icon: '/icons/postgresql.png' },
  { label: 'GaussDB', value: 'gaussdb', icon: '/icons/gaussdb.png' },
  { label: '人大金仓', value: 'kingbase', icon: '/icons/kingbase.png' },
  { label: 'OceanBase', value: 'oceanbase', icon: '/icons/oceanbase.png' },
  { label: '达梦', value: 'dm', icon: '/icons/dm.png' }
]

const rules = {
  name: [{ required: true, message: '请输入连接名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入IP地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  databaseName: [{ required: true, message: '请输入数据库名称', trigger: 'blur' }]
}

const canNext = computed(() => {
  if (currentStep.value === 0) {
    return !!formData.value.type
  }
  return true
})

const handleNext = async () => {
  if (currentStep.value === 1) {
    await formRef.value?.validate()
  }
  currentStep.value++
  
  if (currentStep.value === 2) {
    await loadTables()
  }
}

const testConnection = async () => {
  await formRef.value?.validate()
  testing.value = true
  try {
    await datasourceApi.testConnection(formData.value)
    ElMessage.success('连接成功')
  } catch (error) {
    ElMessage.error(error.message || '连接失败')
  } finally {
    testing.value = false
  }
}

const loadTables = async () => {
  loadingTables.value = true
  try {
    // 先创建数据源获取ID，再获取表列表
    const ds = await datasourceApi.create(formData.value)
    createdDsId.value = ds.id
    const tables = await datasourceApi.getTables(ds.id)
    tableList.value = tables.map(t => ({
      ...t,
      rowCount: 0,
      updateTime: ''
    }))
  } catch (error) {
    ElMessage.error(error.message || '获取表列表失败')
  } finally {
    loadingTables.value = false
  }
}

const handleSelectAll = (val) => {
  if (val) {
    tableList.value.forEach(row => {
      tableRef.value?.toggleRowSelection(row, true)
    })
  } else {
    tableRef.value?.clearSelection()
  }
}

const handleSelectionChange = (rows) => {
  selectedTables.value = rows.map(r => r.tableName)
  // 同步更新全选状态
  selectAll.value = rows.length === tableList.value.length && tableList.value.length > 0
}

const progressFormat = (percentage) => {
  return `${percentage}%`
}

const pollSyncProgress = async (taskId) => {
  try {
    const task = await datasourceApi.getSyncProgress(taskId)
    if (task.totalCount > 0) {
      syncProgress.value = Math.round((task.currentCount / task.totalCount) * 100)
    }
    currentSyncTable.value = task.currentTable || ''

    if (task.status === 'SUCCESS') {
      clearInterval(pollTimer)
      syncing.value = false
      ElMessage.success('数据源创建成功')
      emit('success')
      visible.value = false
    } else if (task.status === 'FAILED') {
      clearInterval(pollTimer)
      syncing.value = false
      ElMessage.error(task.errorMessage || '同步失败')
    }
  } catch (error) {
    clearInterval(pollTimer)
    syncing.value = false
    ElMessage.error('获取同步进度失败')
  }
}

const handleSubmit = async () => {
  if (selectedTables.value.length === 0) {
    ElMessage.warning('请至少选择一个表')
    return
  }
  
  if (!createdDsId.value) {
    ElMessage.error('数据源未创建，请重试')
    return
  }
  
  submitting.value = true
  syncing.value = true
  syncProgress.value = 0
  currentSyncTable.value = ''
  
  try {
    const taskId = await datasourceApi.syncTables(createdDsId.value, selectedTables.value)
    
    pollTimer = setInterval(() => pollSyncProgress(taskId), 1000)
  } catch (error) {
    syncing.value = false
    ElMessage.error(error.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const handleCancel = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  visible.value = false
}

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})

watch(visible, (val) => {
  if (!val) {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    currentStep.value = 0
    syncing.value = false
    syncProgress.value = 0
    currentSyncTable.value = ''
    createdDsId.value = null
    formData.value = {
      name: '',
      type: 'mysql',
      host: '',
      port: 3306,
      databaseName: '',
      username: '',
      password: ''
    }
    tableList.value = []
    selectedTables.value = []
  }
})
</script>

<style lang="less" scoped>
.steps {
  margin-bottom: 30px;
}

.step-content {
  min-height: 300px;
  
  .step-type {
    h4 {
      margin-bottom: 20px;
      color: var(--text-primary);
    }
    
    .type-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;
      
      .type-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 10px;
        padding: 20px;
        border: 2px solid var(--border-color);
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.3s;
        
        .db-icon {
          width: 40px;
          height: 40px;
          object-fit: contain;
        }
        
        &:hover {
          border-color: var(--primary-color);
        }
        
        &.active {
          border-color: var(--primary-color);
          background: var(--primary-light);
        }
        
        span {
          font-size: 14px;
        }
      }
    }
  }
  
  .step-config {
    max-width: 500px;
    margin: 0 auto;
    
    .el-button {
      margin-top: 20px;
    }
  }
  
  .step-tables {
    .table-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }
    
    .sync-progress {
      text-align: center;
      padding: 40px 20px;
      
      p {
        margin-bottom: 20px;
        color: var(--text-primary);
      }
      
      .current-table {
        margin-top: 16px;
        font-size: 13px;
        color: var(--text-secondary);
      }
    }
  }
}
</style>
