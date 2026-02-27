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
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { datamodelingApi } from '@/api/datamodeling'
import DataSourcePanel from './components/DataSourcePanel.vue'
import ERDiagramCanvas from './components/ERDiagramCanvas.vue'

const diagramRef = ref(null)
const selectedDsId = ref(null)
const diagramLoading = ref(false)
const analyzing = ref(false)
const analysisProgress = ref(0)

const erData = reactive({
  tables: [],
  relations: [],
  analyzed: false
})

let pollTimer = null

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
      }
    }
  }
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
