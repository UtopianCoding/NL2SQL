<template>
  <div class="ai-model-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <h2>AI 模型配置</h2>
      <div class="header-actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索"
          :prefix-icon="Search"
          clearable
          class="search-input"
          @input="handleSearch"
        />
        <el-button @click="showDefaultDialog">
          <el-icon><Setting /></el-icon>
          系统默认模型
        </el-button>
        <el-button type="primary" @click="showAddDialog">
          <el-icon><Plus /></el-icon>
          添加模型
        </el-button>
      </div>
    </div>

    <!-- 模型卡片列表 -->
    <div class="model-cards" v-loading="loading">
      <div
        v-for="model in modelList"
        :key="model.id"
        class="model-card"
      >
        <div class="card-header">
          <div class="card-title">
            <img
              :src="getProviderIcon(model.provider)"
              class="provider-icon"
              :alt="model.providerName"
            />
            <span class="model-name">{{ model.modelName }}</span>
          </div>
          <el-tag v-if="model.isDefault === 1" type="success" size="small" effect="plain">
            默认模型
          </el-tag>
        </div>
        <div class="card-body">
          <div class="card-info-row">
            <span class="info-label">模型类型</span>
            <span class="info-value">{{ getModelTypeLabel(model.modelType) }}</span>
          </div>
          <div class="card-info-row">
            <span class="info-label">基础模型</span>
            <span class="info-value">{{ model.baseModel }}</span>
          </div>
        </div>
        <div class="card-actions">
          <el-button text type="primary" size="small" @click="handleEdit(model)">编辑</el-button>
          <el-button
            v-if="model.isDefault !== 1"
            text type="primary" size="small"
            @click="handleSetDefault(model)"
          >设为默认</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(model)">删除</el-button>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="!loading && modelList.length === 0" description="暂无模型配置" />
    </div>

    <!-- 添加/编辑模型弹窗 -->
    <AddModelDialog
      v-model="dialogVisible"
      :edit-data="editData"
      @success="loadModelList"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Setting } from '@element-plus/icons-vue'
import { aiModelApi } from '@/api/aiModel'
import AddModelDialog from './AddModelDialog.vue'

// -- provider icon map (SVG data URIs for simplicity) --
const providerIcons = {
  aliyun: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjZmY2YTAwIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+6ZiPPC90ZXh0Pjwvc3ZnPg==',
  qianfan: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMjkzMjdkIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+5Y2DPC90ZXh0Pjwvc3ZnPg==',
  deepseek: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjNDA5NmZmIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjEyIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+RFM8L3RleHQ+PC9zdmc+',
  hunyuan: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMDA2ZWZmIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+5re3PC90ZXh0Pjwvc3ZnPg==',
  spark: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMDBhYWZmIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+6K665L+hPC90ZXh0Pjwvc3ZnPg==',
  gemini: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjZjRiNDAwIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjEyIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+R2U8L3RleHQ+PC9zdmc+',
  openai: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMTBhMzdiIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjEyIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+QUk8L3RleHQ+PC9zdmc+',
  kimi: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMWExYTFhIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjExIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+S2k8L3RleHQ+PC9zdmc+',
  tencentcloud: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMDA2OWZmIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+6IWNPC90ZXh0Pjwvc3ZnPg==',
  volcengine: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMzM3MGZmIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+54GrPC90ZXh0Pjwvc3ZnPg==',
  openai_compatible: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMTBhMzdiIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjEwIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+QVBJPC90ZXh0Pjwvc3ZnPg==',
}

const loading = ref(false)
const searchKeyword = ref('')
const modelList = ref([])
const dialogVisible = ref(false)
const editData = ref(null)

const getProviderIcon = (provider) => {
  return providerIcons[provider] || providerIcons.openai_compatible
}

const getModelTypeLabel = (type) => {
  const map = { LLM: '大语言模型', Embedding: '向量模型' }
  return map[type] || type
}

const loadModelList = async () => {
  loading.value = true
  try {
    modelList.value = await aiModelApi.getList(searchKeyword.value) || []
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

let searchTimer = null
const handleSearch = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    loadModelList()
  }, 300)
}

const showAddDialog = () => {
  editData.value = null
  dialogVisible.value = true
}

const showDefaultDialog = async () => {
  try {
    const defaultModel = await aiModelApi.getDefault()
    if (defaultModel) {
      ElMessage.info(`当前默认模型: ${defaultModel.modelName} (${defaultModel.baseModel})`)
    } else {
      ElMessage.info('尚未设置默认模型')
    }
  } catch (e) {
    // ignore
  }
}

const handleEdit = (model) => {
  editData.value = { ...model }
  dialogVisible.value = true
}

const handleSetDefault = async (model) => {
  try {
    await ElMessageBox.confirm(
      `确定将「${model.modelName}」设为默认模型吗？`,
      '设置默认模型',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    await aiModelApi.setDefault(model.id)
    ElMessage.success('设置成功')
    loadModelList()
  } catch (e) {
    // cancelled
  }
}

const handleDelete = async (model) => {
  try {
    await ElMessageBox.confirm(
      `确定删除模型「${model.modelName}」吗？此操作不可恢复。`,
      '删除模型',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning', confirmButtonClass: 'el-button--danger' }
    )
    await aiModelApi.delete(model.id)
    ElMessage.success('删除成功')
    loadModelList()
  } catch (e) {
    // cancelled
  }
}

onMounted(() => {
  loadModelList()
})
</script>

<style lang="less" scoped>
.ai-model-page {
  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 24px;

    h2 {
      margin: 0;
      font-size: 24px;
      font-weight: 600;
      color: #1f2937;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;

      .search-input {
        width: 220px;
      }
    }
  }

  .model-cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
    gap: 16px;

    .model-card {
      background: #fff;
      border: 1px solid #e5e7eb;
      border-radius: 8px;
      padding: 20px;
      transition: box-shadow 0.2s;

      &:hover {
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
      }

      .card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 16px;

        .card-title {
          display: flex;
          align-items: center;
          gap: 10px;

          .provider-icon {
            width: 32px;
            height: 32px;
            border-radius: 6px;
          }

          .model-name {
            font-size: 16px;
            font-weight: 600;
            color: #1f2937;
          }
        }
      }

      .card-body {
        .card-info-row {
          display: flex;
          align-items: center;
          margin-bottom: 8px;
          font-size: 14px;

          .info-label {
            color: #10b981;
            min-width: 70px;
            margin-right: 12px;
          }

          .info-value {
            color: #374151;
          }
        }
      }

      .card-actions {
        display: flex;
        align-items: center;
        gap: 4px;
        margin-top: 12px;
        padding-top: 12px;
        border-top: 1px solid #f3f4f6;
      }
    }
  }
}
</style>
