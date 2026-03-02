<template>
  <div class="datamodeling-page">
    <div class="page-header">
      <h2 class="page-title">数据建模</h2>
      <div class="header-actions">
        <el-button
          type="primary"
          :loading="analyzing"
          :disabled="!selectedDsId"
          @click="handleAnalyze"
        >
          <el-icon><Refresh /></el-icon>
          重新分析
        </el-button>
        <el-button :disabled="!selectedDsId" @click="handleFitView">
          <el-icon><FullScreen /></el-icon>
          适应画布
        </el-button>
        <el-button :disabled="!selectedDsId" @click="handleAddRelation">
          <el-icon><Plus /></el-icon>
          新增关系
        </el-button>
        <el-button
          type="success"
          :loading="publishing"
          :disabled="!selectedDsId || erData.tables.length === 0"
          @click="handlePublish"
        >
          <el-icon><Upload /></el-icon>
          发布
        </el-button>
      </div>
    </div>

    <div class="page-content">
      <DataSourcePanel
        :selected-id="selectedDsId"
        @select="handleSelectDataSource"
      />
      <div class="diagram-area">
        <ERDiagramCanvas
          ref="diagramRef"
          :tables="erData.tables"
          :relations="erData.relations"
          :loading="diagramLoading"
          :ds-id="selectedDsId"
          @refresh="handleRefreshDiagram"
        />
        <!-- 底部进度条 -->
        <div v-if="analyzing" class="analysis-bar">
          <el-icon class="rotating"><Loading /></el-icon>
          <span>AI正在分析表关系...</span>
          <el-progress
            :percentage="analysisProgress"
            :stroke-width="10"
            style="flex: 1; margin-left: 12px"
          />
        </div>
        <div v-if="publishing" class="analysis-bar publish-bar">
          <el-icon class="rotating"><Loading /></el-icon>
          <span>{{ publishStatus }}</span>
          <el-progress
            :percentage="publishProgress"
            :stroke-width="10"
            color="#67C23A"
            style="flex: 1; margin-left: 12px"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { datamodelingApi } from '@/api/datamodeling'
import DataSourcePanel from './components/DataSourcePanel.vue'
import ERDiagramCanvas from './components/ERDiagramCanvas.vue'

const diagramRef = ref(null)
const selectedDsId = ref(null)
const diagramLoading = ref(false)
const analyzing = ref(false)
const analysisProgress = ref(0)
const publishing = ref(false)
const publishProgress = ref(0)
const publishStatus = ref('正在发布...')

const erData = reactive({
  tables: [],
  relations: [],
  analyzed: false
})

let pollTimer = null
let publishPollTimer = null

const handleSelectDataSource = async (dsId) => {
  selectedDsId.value = dsId
  await loadERDiagram(dsId)

  // 自动触发AI分析（如果尚未分析过）
  if (!erData.analyzed && erData.tables.length > 0) {
    await startAnalysis(dsId)
  }
}

const loadERDiagram = async (dsId) => {
  diagramLoading.value = true
  try {
    const data = await datamodelingApi.getERDiagram(dsId)
    erData.tables = data.tables || []
    erData.relations = data.relations || []
    erData.analyzed = data.analyzed || false
  } catch (e) {
    ElMessage.error('加载ER图数据失败: ' + (e.message || '未知错误'))
  } finally {
    diagramLoading.value = false
  }
}

const handleAnalyze = () => {
  if (!selectedDsId.value) return
  startAnalysis(selectedDsId.value)
}

const startAnalysis = async (dsId) => {
  analyzing.value = true
  analysisProgress.value = 0
  try {
    const taskId = await datamodelingApi.analyze(dsId)
    pollAnalysisProgress(taskId, dsId)
  } catch (e) {
    analyzing.value = false
    ElMessage.error('触发AI分析失败: ' + (e.message || '未知错误'))
  }
}

const pollAnalysisProgress = (taskId, dsId) => {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    try {
      const task = await datamodelingApi.getAnalysisProgress(taskId)
      if (task.totalCount > 0) {
        analysisProgress.value = Math.floor((task.currentCount / task.totalCount) * 100)
      }
      if (task.status === 'SUCCESS') {
        clearInterval(pollTimer)
        pollTimer = null
        analyzing.value = false
        analysisProgress.value = 100
        ElMessage.success('表关系分析完成')
        await loadERDiagram(dsId)
      } else if (task.status === 'FAILED') {
        clearInterval(pollTimer)
        pollTimer = null
        analyzing.value = false
        ElMessage.error('分析失败: ' + (task.errorMessage || '未知错误'))
      }
    } catch (e) {
      clearInterval(pollTimer)
      pollTimer = null
      analyzing.value = false
    }
  }, 2000)
}

const handleFitView = () => {
  diagramRef.value?.fitView()
}

const handleAddRelation = () => {
  diagramRef.value?.openAddRelationDialog()
}

const handleRefreshDiagram = () => {
  if (selectedDsId.value) {
    loadERDiagram(selectedDsId.value)
  }
}

const handlePublish = async () => {
  if (!selectedDsId.value) return
  try {
    await ElMessageBox.confirm(
      '发布将把当前建模的表结构、字段信息同步到Neo4j图谱，供智能问数使用。确定发布？',
      '发布确认',
      {
        confirmButtonText: '确定发布',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    publishing.value = true
    publishProgress.value = 0
    publishStatus.value = '正在发布...'
    const taskId = await datamodelingApi.publish(selectedDsId.value)
    pollPublishProgress(taskId)
  } catch (e) {
    if (e !== 'cancel') {
      publishing.value = false
      ElMessage.error('发布失败: ' + (e.message || '未知错误'))
    }
  }
}

const pollPublishProgress = (taskId) => {
  if (publishPollTimer) clearInterval(publishPollTimer)
  publishPollTimer = setInterval(async () => {
    try {
      const task = await datamodelingApi.getAnalysisProgress(taskId)
      if (task.totalCount > 0) {
        publishProgress.value = Math.floor((task.currentCount / task.totalCount) * 100)
      }
      if (task.currentTable) {
        publishStatus.value = task.currentTable
      }
      if (task.status === 'SUCCESS') {
        clearInterval(publishPollTimer)
        publishPollTimer = null
        publishing.value = false
        publishProgress.value = 100
        ElMessage.success('发布成功，建模数据已同步到图谱')
      } else if (task.status === 'FAILED') {
        clearInterval(publishPollTimer)
        publishPollTimer = null
        publishing.value = false
        ElMessage.error('发布失败: ' + (task.errorMessage || '未知错误'))
      }
    } catch (e) {
      clearInterval(publishPollTimer)
      publishPollTimer = null
      publishing.value = false
    }
  }, 1000)
}
</script>

<style lang="less" scoped>
.datamodeling-page {
  height: 100%;
  display: flex;
  flex-direction: column;

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .page-title {
      font-size: 20px;
      font-weight: 600;
      margin: 0;
    }

    .header-actions {
      display: flex;
      gap: 10px;
    }
  }

  .page-content {
    flex: 1;
    display: flex;
    gap: 16px;
    overflow: hidden;

    .diagram-area {
      flex: 1;
      display: flex;
      flex-direction: column;
      background: #fff;
      border-radius: 12px;
      border: 1px solid var(--border-color);
      overflow: hidden;
      position: relative;

      .analysis-bar {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 20px;
        background: #f0f7ff;
        border-top: 1px solid #d0e3f7;
        font-size: 13px;
        color: #409eff;

        .rotating {
          animation: rotate 1.5s linear infinite;
        }

        &.publish-bar {
          background: #f0f9eb;
          border-top-color: #c2e7b0;
          color: #67C23A;
        }
      }
    }
  }
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
