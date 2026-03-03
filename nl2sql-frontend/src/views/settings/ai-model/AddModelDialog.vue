<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑模型' : '添加模型'"
    width="900px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <!-- 步骤条 -->
    <div class="step-bar" v-if="!isEdit">
      <div class="step" :class="{ active: currentStep >= 1, done: currentStep > 1 }">
        <span class="step-num">{{ currentStep > 1 ? '&#10003;' : '1' }}</span>
        <span class="step-text">选择供应商</span>
      </div>
      <div class="step-line" :class="{ active: currentStep > 1 }"></div>
      <div class="step" :class="{ active: currentStep >= 2 }">
        <span class="step-num">2</span>
        <span class="step-text">添加模型</span>
      </div>
    </div>

    <el-divider v-if="!isEdit" style="margin: 16px 0 24px" />

    <!-- Step 1: 选择供应商 -->
    <div v-if="!isEdit && currentStep === 1" class="step-content">
      <h4 class="section-title">选择供应商</h4>
      <el-input
        v-model="providerSearch"
        placeholder="搜索"
        :prefix-icon="Search"
        clearable
        class="provider-search"
      />
      <div class="provider-grid">
        <div
          v-for="p in filteredProviders"
          :key="p.id"
          class="provider-item"
          :class="{ selected: selectedProvider?.id === p.id }"
          @click="selectProvider(p)"
        >
          <img :src="getProviderIcon(p.id)" class="provider-icon" :alt="p.name" />
          <span class="provider-name">{{ p.name }}</span>
        </div>
      </div>
    </div>

    <!-- Step 2: 模型配置（添加模式） / 编辑模式全量表单 -->
    <div v-if="isEdit || currentStep === 2" class="step-content step2-layout">
      <!-- 左侧供应商列表（仅添加模式显示） -->
      <div v-if="!isEdit" class="provider-sidebar">
        <el-input
          v-model="providerSearch"
          placeholder="搜索"
          :prefix-icon="Search"
          clearable
          size="small"
          class="sidebar-search"
        />
        <div class="sidebar-list">
          <div
            v-for="p in filteredProviders"
            :key="p.id"
            class="sidebar-item"
            :class="{ active: selectedProvider?.id === p.id }"
            @click="switchProvider(p)"
          >
            <img :src="getProviderIcon(p.id)" class="sidebar-icon" :alt="p.name" />
            <span>{{ p.name }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧表单区域 -->
      <div class="form-area">
        <h4 class="section-title">{{ isEdit ? editData.providerName : selectedProvider?.name }}</h4>

        <el-form
          ref="formRef"
          :model="form"
          :rules="formRules"
          label-position="top"
          class="model-form"
        >
          <el-form-item label="模型名称" prop="modelName" required>
            <template #label>
              <span>模型名称 *</span>
              <el-tooltip content="用于标识此模型配置，可自定义" placement="top">
                <el-icon style="margin-left: 4px; vertical-align: middle;"><InfoFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-input v-model="form.modelName" placeholder="请输入模型名称" />
          </el-form-item>

          <el-form-item label="模型类型">
            <el-select v-model="form.modelType" style="width: 100%">
              <el-option label="大语言模型" value="LLM" />
              <el-option label="向量模型" value="Embedding" />
            </el-select>
          </el-form-item>

          <el-form-item prop="baseModel" required>
            <template #label>
              <span>基础模型 *</span>
              <span class="base-model-hint">列表中未列出的模型，直接输入模型名称，回车即可添加</span>
            </template>
            <el-select
              v-model="form.baseModel"
              filterable
              allow-create
              default-first-option
              placeholder="请选择"
              style="width: 100%"
            >
              <el-option
                v-for="m in currentBaseModels"
                :key="m"
                :label="m"
                :value="m"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="API 域名" prop="apiUrl" required>
            <template #label><span>API 域名 *</span></template>
            <el-input v-model="form.apiUrl" placeholder="请输入API域名" />
          </el-form-item>

          <el-form-item label="API Key" prop="apiKey" required>
            <template #label><span>API Key *</span></template>
            <el-input
              v-model="form.apiKey"
              :placeholder="isEdit ? '不修改请留空' : '请输入API Key'"
              show-password
            />
          </el-form-item>

          <!-- 高级设置 -->
          <div class="advanced-section">
            <div class="advanced-toggle" @click="showAdvanced = !showAdvanced">
              <span>高级设置</span>
              <el-icon>
                <ArrowUp v-if="showAdvanced" />
                <ArrowDown v-else />
              </el-icon>
            </div>

            <div v-if="showAdvanced" class="advanced-content">
              <div class="params-header">
                <span>模型参数</span>
                <el-button text type="primary" size="small" @click="addParam">
                  <el-icon><Plus /></el-icon> 添加
                </el-button>
              </div>

              <el-table :data="paramList" border size="small" v-if="paramList.length > 0">
                <el-table-column label="参数" prop="key" min-width="120">
                  <template #default="{ row, $index }">
                    <el-input v-model="row.key" size="small" placeholder="参数名" />
                  </template>
                </el-table-column>
                <el-table-column label="显示名称" prop="label" min-width="120">
                  <template #default="{ row }">
                    <el-input v-model="row.label" size="small" placeholder="显示名称" />
                  </template>
                </el-table-column>
                <el-table-column label="参数值" prop="value" min-width="140">
                  <template #default="{ row }">
                    <el-input v-model="row.value" size="small" placeholder="参数值" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center">
                  <template #default="{ $index }">
                    <el-button text type="primary" size="small" @click="editParam($index)">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button text type="danger" size="small" @click="removeParam($index)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 底部按钮 -->
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="$emit('update:modelValue', false)">取消</el-button>
        <el-button v-if="!isEdit && currentStep === 2" @click="currentStep = 1">上一步</el-button>
        <el-button
          v-if="!isEdit && currentStep === 1"
          type="primary"
          :disabled="!selectedProvider"
          @click="goStep2"
        >下一步</el-button>
        <el-button
          v-if="isEdit || currentStep === 2"
          type="primary"
          :loading="saving"
          @click="handleSave"
        >保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, Edit, Delete, ArrowUp, ArrowDown, InfoFilled } from '@element-plus/icons-vue'
import { aiModelApi } from '@/api/aiModel'

const props = defineProps({
  modelValue: Boolean,
  editData: Object
})

const emit = defineEmits(['update:modelValue', 'success'])

// 供应商配置数据
const providers = [
  {
    id: 'aliyun',
    name: '阿里云百炼',
    defaultUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: ['qwen-plus', 'qwen-turbo', 'qwen-max', 'qwen-long', 'qwen2.5-72b-instruct', 'qwen2.5-32b-instruct']
  },
  {
    id: 'qianfan',
    name: '千帆大模型',
    defaultUrl: 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1',
    models: ['ernie-4.0-8k', 'ernie-3.5-8k', 'ernie-speed-128k', 'ernie-lite-8k']
  },
  {
    id: 'deepseek',
    name: 'DeepSeek',
    defaultUrl: 'https://api.deepseek.com/v1',
    models: ['deepseek-chat', 'deepseek-coder', 'deepseek-reasoner']
  },
  {
    id: 'hunyuan',
    name: '腾讯混元',
    defaultUrl: 'https://hunyuan.tencentcloudapi.com',
    models: ['hunyuan-pro', 'hunyuan-standard', 'hunyuan-lite']
  },
  {
    id: 'spark',
    name: '讯飞星火',
    defaultUrl: 'https://spark-api-open.xf-yun.com/v1',
    models: ['spark-4.0-ultra', 'spark-max', 'spark-pro', 'spark-lite']
  },
  {
    id: 'gemini',
    name: 'Gemini',
    defaultUrl: 'https://generativelanguage.googleapis.com/v1beta',
    models: ['gemini-2.0-flash', 'gemini-1.5-pro', 'gemini-1.5-flash']
  },
  {
    id: 'openai',
    name: 'OpenAI',
    defaultUrl: 'https://api.openai.com/v1',
    models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'gpt-3.5-turbo']
  },
  {
    id: 'kimi',
    name: 'Kimi',
    defaultUrl: 'https://api.moonshot.cn/v1',
    models: ['moonshot-v1-128k', 'moonshot-v1-32k', 'moonshot-v1-8k']
  },
  {
    id: 'tencentcloud',
    name: '腾讯云',
    defaultUrl: 'https://api.lkeap.cloud.tencent.com/v1',
    models: ['deepseek-v3', 'deepseek-r1']
  },
  {
    id: 'volcengine',
    name: '火山引擎',
    defaultUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    models: ['doubao-pro-32k', 'doubao-lite-32k', 'doubao-pro-128k']
  },
  {
    id: 'openai_compatible',
    name: '通用OpenAI',
    defaultUrl: '',
    models: []
  },
]

// provider icon map (same as parent)
const providerIcons = {
  aliyun: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjZmY2YTAwIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+6ZiPPC90ZXh0Pjwvc3ZnPg==',
  qianfan: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMjkzMjdkIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+5Y2DPC90ZXh0Pjwvc3ZnPg==',
  deepseek: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjNDA5NmZmIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjEyIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+RFM8L3RleHQ+PC9zdmc+',
  hunyuan: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMDA2ZWZmIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+5re3PC90ZXh0Pjwvc3ZnPg==',
  spark: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMDBhYWZmIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjEyIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+5pifPC90ZXh0Pjwvc3ZnPg==',
  gemini: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjZjRiNDAwIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjEyIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+R2U8L3RleHQ+PC9zdmc+',
  openai: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMTBhMzdiIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjEyIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+QUk8L3RleHQ+PC9zdmc+',
  kimi: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMWExYTFhIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjExIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+S2k8L3RleHQ+PC9zdmc+',
  tencentcloud: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMDA2OWZmIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+6IWNPC90ZXh0Pjwvc3ZnPg==',
  volcengine: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMzM3MGZmIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjE2IiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+54GrPC90ZXh0Pjwvc3ZnPg==',
  openai_compatible: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIj48cmVjdCB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHJ4PSI2IiBmaWxsPSIjMTBhMzdiIi8+PHRleHQgeD0iMTYiIHk9IjIyIiBmb250LXNpemU9IjEwIiBmaWxsPSIjZmZmIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiI+QVBJPC90ZXh0Pjwvc3ZnPg==',
}

const getProviderIcon = (id) => providerIcons[id] || providerIcons.openai_compatible

// State
const currentStep = ref(1)
const providerSearch = ref('')
const selectedProvider = ref(null)
const showAdvanced = ref(false)
const saving = ref(false)
const formRef = ref()

const isEdit = computed(() => !!props.editData)

const form = reactive({
  modelName: '',
  modelType: 'LLM',
  baseModel: '',
  apiUrl: '',
  apiKey: '',
})

const paramList = ref([])

const formRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  baseModel: [{ required: true, message: '请选择或输入基础模型', trigger: 'change' }],
  apiUrl: [{ required: true, message: '请输入API域名', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入API Key', trigger: 'blur' }],
}

const filteredProviders = computed(() => {
  if (!providerSearch.value) return providers
  const kw = providerSearch.value.toLowerCase()
  return providers.filter(p => p.name.toLowerCase().includes(kw) || p.id.toLowerCase().includes(kw))
})

const currentBaseModels = computed(() => {
  if (isEdit.value) {
    const p = providers.find(p => p.id === props.editData?.provider)
    return p?.models || []
  }
  return selectedProvider.value?.models || []
})

// Watch editData to populate form
watch(() => props.editData, (val) => {
  if (val) {
    form.modelName = val.modelName || ''
    form.modelType = val.modelType || 'LLM'
    form.baseModel = val.baseModel || ''
    form.apiUrl = val.apiUrl || ''
    form.apiKey = ''
    // Parse params
    if (val.params) {
      try {
        const parsed = JSON.parse(val.params)
        paramList.value = Object.entries(parsed).map(([key, value]) => ({
          key,
          label: key,
          value: typeof value === 'object' ? JSON.stringify(value) : String(value)
        }))
        if (paramList.value.length > 0) showAdvanced.value = true
      } catch {
        paramList.value = []
      }
    } else {
      paramList.value = []
    }
  }
}, { immediate: true })

const selectProvider = (p) => {
  selectedProvider.value = p
}

const goStep2 = () => {
  if (!selectedProvider.value) return
  // Pre-fill defaults
  form.apiUrl = selectedProvider.value.defaultUrl
  form.modelName = ''
  form.baseModel = ''
  form.apiKey = ''
  form.modelType = 'LLM'
  paramList.value = []
  showAdvanced.value = false
  currentStep.value = 2
}

const switchProvider = (p) => {
  selectedProvider.value = p
  form.apiUrl = p.defaultUrl
  form.baseModel = ''
}

const addParam = () => {
  paramList.value.push({ key: '', label: '', value: '' })
}

const editParam = () => {
  // inline edit via table, no-op
}

const removeParam = (index) => {
  paramList.value.splice(index, 1)
}

const handleSave = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  // Build params JSON
  const params = {}
  for (const p of paramList.value) {
    if (p.key) {
      try {
        params[p.key] = JSON.parse(p.value)
      } catch {
        params[p.key] = p.value
      }
    }
  }

  const payload = {
    modelName: form.modelName,
    provider: isEdit.value ? props.editData.provider : selectedProvider.value.id,
    providerName: isEdit.value ? props.editData.providerName : selectedProvider.value.name,
    modelType: form.modelType,
    baseModel: form.baseModel,
    apiUrl: form.apiUrl,
    apiKey: form.apiKey || undefined,
    params: Object.keys(params).length > 0 ? JSON.stringify(params) : null,
  }

  // For edit, don't send apiKey if empty (user didn't change it)
  if (isEdit.value && !form.apiKey) {
    delete payload.apiKey
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await aiModelApi.update(props.editData.id, payload)
      ElMessage.success('更新成功')
    } else {
      await aiModelApi.create(payload)
      ElMessage.success('添加成功')
    }
    emit('update:modelValue', false)
    emit('success')
  } catch (e) {
    // error handled by interceptor
  } finally {
    saving.value = false
  }
}

const handleClosed = () => {
  currentStep.value = 1
  selectedProvider.value = null
  providerSearch.value = ''
  showAdvanced.value = false
  paramList.value = []
  form.modelName = ''
  form.modelType = 'LLM'
  form.baseModel = ''
  form.apiUrl = ''
  form.apiKey = ''
}
</script>

<style lang="less" scoped>
.step-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 0;

  .step {
    display: flex;
    align-items: center;
    gap: 8px;

    .step-num {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: #e5e7eb;
      color: #9ca3af;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      font-weight: 600;
      transition: all 0.3s;
    }

    .step-text {
      font-size: 14px;
      color: #9ca3af;
      transition: color 0.3s;
    }

    &.active {
      .step-num {
        background: #10b981;
        color: #fff;
      }
      .step-text {
        color: #1f2937;
        font-weight: 500;
      }
    }

    &.done {
      .step-num {
        background: #10b981;
        color: #fff;
      }
    }
  }

  .step-line {
    width: 120px;
    height: 2px;
    background: #e5e7eb;
    margin: 0 16px;
    transition: background 0.3s;

    &.active {
      background: #10b981;
    }
  }
}

.section-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.provider-search {
  margin-bottom: 16px;
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;

  .provider-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 16px 20px;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: #10b981;
    }

    &.selected {
      border-color: #10b981;
      background: #f0fdf4;
    }

    .provider-icon {
      width: 36px;
      height: 36px;
      border-radius: 8px;
    }

    .provider-name {
      font-size: 15px;
      color: #374151;
      font-weight: 500;
    }
  }
}

.step2-layout {
  display: flex;
  gap: 0;
  min-height: 450px;

  .provider-sidebar {
    width: 200px;
    border-right: 1px solid #e5e7eb;
    padding-right: 20px;
    flex-shrink: 0;

    .sidebar-search {
      margin-bottom: 12px;
    }

    .sidebar-list {
      .sidebar-item {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 12px;
        border-radius: 6px;
        cursor: pointer;
        font-size: 14px;
        color: #374151;
        transition: all 0.2s;

        &:hover {
          background: #f3f4f6;
        }

        &.active {
          background: #f0fdf4;
          color: #10b981;
          font-weight: 500;
        }

        .sidebar-icon {
          width: 24px;
          height: 24px;
          border-radius: 4px;
        }
      }
    }
  }

  .form-area {
    flex: 1;
    padding-left: 24px;
    overflow-y: auto;
    max-height: 500px;
  }
}

.base-model-hint {
  color: #f59e0b;
  font-size: 12px;
  margin-left: 8px;
  font-weight: 400;
}

.model-form {
  :deep(.el-form-item__label) {
    font-weight: 500;
  }
}

.advanced-section {
  margin-top: 8px;

  .advanced-toggle {
    display: flex;
    align-items: center;
    gap: 4px;
    cursor: pointer;
    font-size: 14px;
    color: #374151;
    font-weight: 500;
    padding: 8px 0;
    user-select: none;

    &:hover {
      color: #10b981;
    }
  }

  .advanced-content {
    margin-top: 12px;

    .params-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
      font-size: 14px;
      font-weight: 500;
      color: #374151;
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
