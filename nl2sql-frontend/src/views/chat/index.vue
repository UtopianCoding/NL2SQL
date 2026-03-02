<template>
  <div class="chat-page">
    <!-- 左侧对话历史 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <h3>智能问数</h3>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索"
          :prefix-icon="Search"
          size="small"
          clearable
        />
      </div>

      <el-button type="primary" class="new-chat-btn" @click="createNewChat">
        <el-icon><Plus /></el-icon>
        新建对话
      </el-button>

      <div class="history-list">
        <div v-if="todayList.length" class="history-group">
          <div class="group-title">
            <el-icon><ArrowDown /></el-icon>
            今天
          </div>
          <div
            v-for="item in todayList"
            :key="item.id"
            class="history-item"
            :class="{ active: currentConversationId === item.id }"
            @click="selectConversation(item)"
          >
            <div class="item-content">
              <div class="item-time">{{ formatTime(item.createTime) }}</div>
              <div class="item-title">{{ item.title || '新对话' }}</div>
            </div>
            <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, item.id)" @click.stop>
              <el-icon class="item-more"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="delete">
                    <el-icon color="#ef4444"><Delete /></el-icon>
                    <span style="color: #ef4444">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <div v-if="weekList.length" class="history-group">
          <div class="group-title">
            <el-icon><ArrowDown /></el-icon>
            7天内
          </div>
          <div
            v-for="item in weekList"
            :key="item.id"
            class="history-item"
            :class="{ active: currentConversationId === item.id }"
            @click="selectConversation(item)"
          >
            <div class="item-content">
              <div class="item-time">{{ formatTime(item.createTime) }}</div>
              <div class="item-title">{{ item.title || '新对话' }}</div>
            </div>
            <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, item.id)" @click.stop>
              <el-icon class="item-more"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="delete">
                    <el-icon color="#ef4444"><Delete /></el-icon>
                    <span style="color: #ef4444">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧主内容区 -->
    <div class="main-area">
      <!-- 顶部标题栏 -->
      <div class="main-header">
        <div class="header-title">自然语言转SQL高性能解决方案</div>
        <el-select v-model="selectedDsId" placeholder="选择数据源" class="ds-select">
          <el-option
            v-for="ds in datasourceStore.list"
            :key="ds.id"
            :label="ds.name"
            :value="ds.id"
          />
        </el-select>
      </div>

      <!-- 对话内容区 -->
      <div class="chat-content" ref="chatContentRef">
        <!-- 欢迎界面（无数据时） -->
        <div v-if="messages.length === 0 && !loading" class="welcome-panel">
          <div class="welcome-card">
            <el-icon :size="48" color="#6366f1"><ChatDotRound /></el-icon>
            <h2>你好，我是 SQLBot</h2>
            <p>请在下方输入您的自然语言问题，我将自动解析数据库结构、生成SQL并执行查询。</p>
          </div>
        </div>

        <!-- 对话消息列表 -->
        <template v-else>
          <div v-for="msg in messages" :key="msg.id" class="chat-message" :class="msg.role">
            <div class="message-avatar">
              <el-avatar v-if="msg.role === 'user'" :size="36">U</el-avatar>
              <el-avatar v-else :size="36" style="background: #6366f1">
                <el-icon><DataAnalysis /></el-icon>
              </el-avatar>
            </div>
            <div class="message-body">
              <!-- 用户消息 -->
              <div v-if="msg.role === 'user'" class="user-message-wrapper">
                <div class="user-message-text">{{ msg.content }}</div>
                <div class="user-message-actions">
                  <el-button link size="small" class="copy-btn" @click="copyText(msg.content)">
                    <el-icon><CopyDocument /></el-icon>
                  </el-button>
                  <el-button link size="small" class="copy-btn" @click="regenerate(msg.content)" :loading="loading">
                    <el-icon><RefreshRight /></el-icon>
                  </el-button>
                </div>
              </div>

              <!-- 助手消息：整合三个功能模块 -->
              <template v-else>
                <!-- 1. 查询分析 -->
                <div class="response-section analysis-section">
                  <div class="section-header" @click="toggleSection(msg.id, 'analysis')">
                    <el-icon><Search /></el-icon>
                    <span>查询分析</span>
                    <el-icon class="arrow" :class="{ expanded: expandedSections[msg.id]?.analysis !== false }">
                      <ArrowDown />
                    </el-icon>
                  </div>
                  <el-collapse-transition>
                    <div v-show="expandedSections[msg.id]?.analysis !== false" class="section-content">
                      <div class="mini-steps">
                        <div class="mini-step done"><el-icon><SuccessFilled /></el-icon>解析数据源结构</div>
                        <div class="mini-step done"><el-icon><SuccessFilled /></el-icon>生成SQL语句</div>
                        <div class="mini-step done"><el-icon><SuccessFilled /></el-icon>执行查询</div>
                        <div class="mini-step done"><el-icon><SuccessFilled /></el-icon>分析完成</div>
                      </div>
                      <div v-if="msg.resultRows != null" class="analysis-result">
                        查询成功，共返回 <strong>{{ msg.resultRows }}</strong> 条结果，耗时 <strong>{{ msg.executionTime }}ms</strong>
                      </div>
                    </div>
                  </el-collapse-transition>
                </div>

                <!-- 2. SQL语句及解释 -->
                <div v-if="msg.sql" class="response-section sql-section">
                  <div class="section-header" @click="toggleSection(msg.id, 'sql')">
                    <el-icon><DataLine /></el-icon>
                    <span>SQL语句及解释</span>
                    <el-icon class="arrow" :class="{ expanded: expandedSections[msg.id]?.sql !== false }">
                      <ArrowDown />
                    </el-icon>
                  </div>
                  <el-collapse-transition>
                    <div v-show="expandedSections[msg.id]?.sql !== false" class="section-content">
                      <div class="sql-block">
                        <div class="sql-toolbar">
                          <span>生成的SQL</span>
                          <el-button type="primary" link size="small" @click.stop="copySql(msg.sql)">
                            <el-icon><CopyDocument /></el-icon>复制
                          </el-button>
                        </div>
                        <pre><code>{{ msg.sql }}</code></pre>
                      </div>
                      <div v-if="msg.content && msg.content !== '查询完成' && msg.content !== '查询失败'" class="explanation-block">
                        <div class="explanation-title">SQL解释</div>
                        <div class="explanation-text markdown-body" v-html="md.render(msg.content)"></div>
                      </div>
                    </div>
                  </el-collapse-transition>
                </div>

                <!-- 3. 可视化图表 -->
                <div v-if="msg.data && msg.data.length > 0" class="response-section chart-section">
                  <div class="section-header" @click="toggleSection(msg.id, 'chart')">
                    <el-icon><TrendCharts /></el-icon>
                    <span>可视化图表</span>
                    <el-icon class="arrow" :class="{ expanded: expandedSections[msg.id]?.chart !== false }">
                      <ArrowDown />
                    </el-icon>
                  </div>
                  <el-collapse-transition>
                    <div v-show="expandedSections[msg.id]?.chart !== false" class="section-content">
                      <div class="chart-wrapper">
                        <div class="chart-toolbar">
                          <el-radio-group
                            :model-value="messageChartTypes[msg.id] || 'bar'"
                            size="small"
                            @change="(val) => setMessageChartType(msg.id, val)"
                          >
                            <el-radio-button value="bar">柱状图</el-radio-button>
                            <el-radio-button value="line">折线图</el-radio-button>
                            <el-radio-button value="pie">饼图</el-radio-button>
                          </el-radio-group>
                        </div>
                        <v-chart
                          v-if="getMessageChartOption(msg)"
                          class="message-chart"
                          :option="getMessageChartOption(msg)"
                          autoresize
                        />
                        <div v-else class="chart-empty">当前数据不适合图表展示</div>
                      </div>
                      <!-- 数据表格 -->
                      <div class="data-table-wrapper">
                        <div class="table-title">查询结果</div>
                        <el-table :data="msg.data" max-height="300" border size="small" stripe>
                          <el-table-column
                            v-for="col in Object.keys(msg.data[0] || {})"
                            :key="col"
                            :prop="col"
                            :label="col"
                            min-width="100"
                            show-overflow-tooltip
                          />
                        </el-table>
                      </div>
                    </div>
                  </el-collapse-transition>
                </div>

                <!-- 错误信息 -->
                <div v-if="msg.error" class="response-error">
                  <el-alert :title="msg.error" type="error" :closable="false" />
                </div>
              </template>
            </div>
          </div>

          <!-- 加载中动画 -->
          <div v-if="loading" class="chat-message assistant">
            <div class="message-avatar">
              <el-avatar :size="36" style="background: #6366f1">
                <el-icon><DataAnalysis /></el-icon>
              </el-avatar>
            </div>
            <div class="message-body">
              <div class="loading-response">
                <div class="response-section analysis-section">
                  <div class="section-header">
                    <el-icon><Search /></el-icon>
                    <span>查询分析</span>
                    <span class="loading-dots"><i></i><i></i></span>
                  </div>
                  <div class="section-content">
                    <div class="mini-steps">
                      <div
                        v-for="(step, idx) in analysisSteps"
                        :key="idx"
                        class="mini-step"
                        :class="step.status"
                      >
                        <el-icon v-if="step.status === 'done'"><SuccessFilled /></el-icon>
                        <el-icon v-else-if="step.status === 'loading'" class="is-loading"><Loading /></el-icon>
                        <el-icon v-else><CircleCheck /></el-icon>
                        {{ step.title }}
                        <span v-if="step.status === 'loading'" class="step-hint">{{ step.desc }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 底部输入区 -->
      <div class="input-area">
        <div class="input-wrapper">
          <el-icon class="input-icon"><EditPen /></el-icon>
          <el-input
            v-model="inputText"
            placeholder="例如："
            @keydown.enter.exact="sendMessage"
            :disabled="loading"
            class="question-input"
          />
          <el-button
            :type="loading ? 'info' : 'primary'"
            :loading="loading"
            :disabled="!canSend"
            @click="sendMessage"
            class="send-btn"
            round
          >
            {{ loading ? '分析中' : '发送' }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, reactive } from 'vue'
import { useDataSourceStore } from '@/stores/datasource'
import { chatApi } from '@/api/chat'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Plus, ArrowDown, CopyDocument, EditPen, Delete, MoreFilled,
  SuccessFilled, Loading, CircleCheck, DataLine, TrendCharts, DataAnalysis,
  ChatDotRound, RefreshRight
} from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, DatasetComponent
} from 'echarts/components'
import dayjs from 'dayjs'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt()

use([
  CanvasRenderer, BarChart, LineChart, PieChart,
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, DatasetComponent
])

const datasourceStore = useDataSourceStore()

// --- 状态 ---
const searchKeyword = ref('')
const inputText = ref('')
const selectedDsId = ref()
const currentConversationId = ref()
const conversations = ref([])
const loading = ref(false)
const chatContentRef = ref()

// 当前会话的所有消息
const messages = ref([])

// 每条消息的展开/折叠状态 { [msgId]: { analysis: true, sql: true, chart: true } }
const expandedSections = reactive({})

// 每条消息的图表类型 { [msgId]: 'bar' | 'line' | 'pie' }
const messageChartTypes = reactive({})

// 分析步骤
const analysisSteps = ref([])

const STEPS_TEMPLATE = [
  { title: '解析数据源', desc: '正在从图数据库解析相关表结构...' },
  { title: '生成SQL语句', desc: '正在通过大模型生成SQL...' },
  { title: '执行SQL查询', desc: '正在执行SQL查询获取数据...' },
  { title: '分析查询结果', desc: '正在整理并分析查询结果...' }
]

// --- 计算属性 ---
const todayList = computed(() => {
  const today = dayjs().startOf('day')
  return conversations.value.filter(c => dayjs(c.createTime).isAfter(today))
})

const weekList = computed(() => {
  const today = dayjs().startOf('day')
  const weekAgo = dayjs().subtract(7, 'day').startOf('day')
  return conversations.value.filter(c => {
    const time = dayjs(c.createTime)
    return time.isBefore(today) && time.isAfter(weekAgo)
  })
})

const canSend = computed(() => {
  return inputText.value.trim() && selectedDsId.value && !loading.value
})

// --- 折叠/展开控制 ---
const toggleSection = (msgId, section) => {
  if (!expandedSections[msgId]) {
    expandedSections[msgId] = { analysis: true, sql: true, chart: true }
  }
  expandedSections[msgId][section] = !expandedSections[msgId][section]
}

// --- 每条消息的图表类型控制 ---
const getMessageChartType = (msgId) => {
  return messageChartTypes[msgId] || 'bar'
}

const setMessageChartType = (msgId, type) => {
  messageChartTypes[msgId] = type
}

// --- 图表工具函数 ---
/**
 * 判断一个值是否可以作为数值使用（number 或可解析为数字的字符串）
 */
const isNumericValue = (v) => {
  if (v == null) return false
  if (typeof v === 'number') return true
  if (typeof v === 'string') {
    const trimmed = v.trim()
    return trimmed !== '' && !isNaN(Number(trimmed))
  }
  return false
}

/**
 * 将值转为数字，无法转换返回 0
 */
const toNumber = (v) => {
  if (v == null) return 0
  const n = Number(v)
  return isNaN(n) ? 0 : n
}

/**
 * 分析列类型：遍历多行数据综合判断
 * 返回 { categoryCols, valueCols }
 */
const analyzeColumns = (data) => {
  const columns = Object.keys(data[0])
  const categoryCols = []
  const valueCols = []

  for (const col of columns) {
    // 统计非 null 值中有多少是数值型
    let numericCount = 0
    let nonNullCount = 0
    for (const row of data) {
      if (row[col] == null) continue
      nonNullCount++
      if (isNumericValue(row[col])) numericCount++
    }
    if (nonNullCount === 0) continue

    // 超过 80% 的非 null 值是数值 → 数值列，否则 → 分类列
    if (numericCount / nonNullCount >= 0.8) {
      valueCols.push(col)
    } else {
      categoryCols.push(col)
    }
  }

  return { categoryCols, valueCols }
}

// --- 根据消息数据生成图表配置 ---
const getMessageChartOption = (msg) => {
  if (!msg.data || msg.data.length === 0 || !msg.data[0]) return null

  const data = msg.data
  const chartType = getMessageChartType(msg.id)

  let { categoryCols, valueCols } = analyzeColumns(data)

  // 回退策略 1：没有分类列时，用第一个数值列当分类
  if (categoryCols.length === 0 && valueCols.length >= 2) {
    categoryCols = [valueCols.shift()]
  }
  // 回退策略 2：没有数值列时，尝试把除第一列外的列都当数值列
  if (valueCols.length === 0 && categoryCols.length >= 2) {
    const first = categoryCols.shift()
    categoryCols = [first]
    valueCols = Object.keys(data[0]).filter(c => c !== first)
  }
  // 回退策略 3：只有一列时无法画图
  if (categoryCols.length === 0 || valueCols.length === 0) {
    const allCols = Object.keys(data[0])
    const hasAnyNumeric = allCols.some(col => data.some(r => isNumericValue(r[col])))
    if (!hasAnyNumeric) return null
    return buildChartConfig(
      data.map((_, i) => `#${i + 1}`),
      allCols.filter(col => data.some(r => isNumericValue(r[col]))),
      data,
      chartType
    )
  }

  const categoryCol = categoryCols[0]
  const categories = data.map(r => String(r[categoryCol] ?? ''))

  return buildChartConfig(categories, valueCols, data, chartType)
}

function buildChartConfig(categories, valueCols, data, chartType) {
  if (chartType === 'pie') {
    const valueCol = valueCols[0]
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', left: 'left', top: 'center' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {d}%' },
        data: data.map((r, i) => ({
          name: categories[i],
          value: toNumber(r[valueCol])
        }))
      }]
    }
  }

  // bar / line
  return {
    tooltip: { trigger: 'axis' },
    legend: valueCols.length > 1 ? { data: valueCols } : undefined,
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { rotate: categories.length > 8 ? 30 : 0 }
    },
    yAxis: { type: 'value' },
    series: valueCols.map(col => ({
      name: col,
      type: chartType === 'line' ? 'line' : 'bar',
      data: data.map(r => toNumber(r[col])),
      smooth: true,
      itemStyle: { borderRadius: chartType === 'bar' ? [4, 4, 0, 0] : undefined }
    }))
  }
}

// --- 方法 ---
const formatTime = (time) => dayjs(time).format('YYYY-MM-DD HH:mm:ss')

const loadConversations = async () => {
  try {
    const res = await chatApi.getConversationList()
    conversations.value = res.records || []
  } catch (error) {
    console.error('加载对话列表失败', error)
  }
}

const deleteConversation = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该对话吗？删除后无法恢复。', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await chatApi.deleteConversation(id)
    conversations.value = conversations.value.filter(c => c.id !== id)
    // 如果删除的是当前选中的对话，清空主区域
    if (currentConversationId.value === id) {
      currentConversationId.value = null
      messages.value = []
      analysisSteps.value = []
    }
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const handleCommand = (command, id) => {
  if (command === 'delete') {
    deleteConversation(id)
  }
}

const createNewChat = async () => {
  if (!selectedDsId.value) {
    if (datasourceStore.list.length > 0) {
      selectedDsId.value = datasourceStore.list[0].id
    } else {
      ElMessage.warning('请先添加数据源')
      return
    }
  }
  try {
    const conversation = await chatApi.createConversation(selectedDsId.value)
    conversations.value.unshift(conversation)
    currentConversationId.value = conversation.id
    messages.value = []
    analysisSteps.value = []
  } catch (error) {
    ElMessage.error(error.message || '创建对话失败')
  }
}

const selectConversation = async (conversation) => {
  currentConversationId.value = conversation.id
  selectedDsId.value = conversation.dsId

  try {
    const res = await chatApi.getConversation(conversation.id)

    // 填充消息列表
    if (res.messages && res.messages.length > 0) {
      messages.value = res.messages.map(m => ({
        id: m.id,
        role: m.role,
        content: m.content,
        sql: m.sqlText,
        data: m.resultData ? JSON.parse(m.resultData) : null,
        resultRows: m.resultRows,
        executionTime: m.executionTimeMs,
        error: m.errorMessage
      }))
    } else if (res.histories && res.histories.length > 0) {
      // 向后兼容：从 query_history 重建消息
      messages.value = res.histories.flatMap(h => {
        const msgs = [
          { id: h.id * 2, role: 'user', content: h.naturalQuery }
        ]
        msgs.push({
          id: h.id * 2 + 1,
          role: 'assistant',
          content: h.executionStatus === 'SUCCESS' ? '查询完成' : '查询失败',
          sql: h.generatedSql,
          resultRows: h.resultRows,
          executionTime: h.executionTimeMs,
          error: h.executionStatus === 'FAILED' ? (h.errorMessage || '执行出错') : null
        })
        return msgs
      })
    } else {
      messages.value = []
    }
  } catch (error) {
    ElMessage.error(error.message || '加载对话失败')
  }
}

// 模拟分析步骤的逐步动画
const simulateSteps = () => {
  analysisSteps.value = STEPS_TEMPLATE.map(s => ({ ...s, status: 'pending' }))
  // 第一步立即开始
  analysisSteps.value[0].status = 'loading'

  const stepDurations = [800, 1200, 1500] // 每步停留时间
  let timer
  let idx = 0

  const advanceStep = () => {
    if (idx < stepDurations.length && loading.value) {
      analysisSteps.value[idx].status = 'done'
      idx++
      if (idx < analysisSteps.value.length) {
        analysisSteps.value[idx].status = 'loading'
      }
      timer = setTimeout(advanceStep, stepDurations[idx - 1] || 1000)
    }
  }

  timer = setTimeout(advanceStep, stepDurations[0])

  // 返回清理函数
  return () => clearTimeout(timer)
}

const finishAllSteps = () => {
  analysisSteps.value.forEach(s => { s.status = 'done' })
}

const sendMessage = async () => {
  if (!canSend.value) return

  const question = inputText.value.trim()
  inputText.value = ''

  // 添加用户消息到列表
  const userMsgId = Date.now()
  messages.value.push({
    id: userMsgId,
    role: 'user',
    content: question
  })

  await nextTick()
  scrollToBottom()

  loading.value = true
  const cleanupSteps = simulateSteps()

  try {
    const res = await chatApi.query({
      dsId: selectedDsId.value,
      question,
      conversationId: currentConversationId.value
    })

    if (!currentConversationId.value) {
      currentConversationId.value = res.conversationId
    }

    finishAllSteps()

    // 添加助手消息到列表
    const assistantMsg = {
      id: res.historyId || Date.now() + 1,
      role: 'assistant',
      content: res.explanation || (res.status === 'SUCCESS' ? '查询完成' : '查询失败'),
      sql: res.sql,
      data: res.data,
      resultRows: res.data?.length || 0,
      executionTime: res.executionTimeMs,
      error: res.errorMessage
    }
    messages.value.push(assistantMsg)

    loadConversations()
  } catch (error) {
    finishAllSteps()
    const errorMsg = {
      id: Date.now() + 1,
      role: 'assistant',
      content: '查询失败',
      error: error.message || '查询失败，请重试'
    }
    messages.value.push(errorMsg)
  } finally {
    loading.value = false
    cleanupSteps()
    await nextTick()
    scrollToBottom()
  }
}

const scrollToBottom = () => {
  if (chatContentRef.value) {
    chatContentRef.value.scrollTop = chatContentRef.value.scrollHeight
  }
}

const copySql = (sql) => {
  navigator.clipboard.writeText(sql)
  ElMessage.success('SQL已复制到剪贴板')
}

const copyText = (text) => {
  navigator.clipboard.writeText(text)
  ElMessage.success('已复制到剪贴板')
}

const regenerate = async (question) => {
  if (loading.value || !question) return

  // 移除上一次该问题对应的助手回复（紧跟在最后一条同内容用户消息之后的助手消息）
  const lastUserIdx = messages.value.map((m, i) => ({ m, i }))
    .filter(({ m }) => m.role === 'user' && m.content === question)
    .pop()?.i
  if (lastUserIdx != null && lastUserIdx + 1 < messages.value.length && messages.value[lastUserIdx + 1].role === 'assistant') {
    messages.value.splice(lastUserIdx + 1, 1)
  }

  await nextTick()
  scrollToBottom()

  loading.value = true
  const cleanupSteps = simulateSteps()

  try {
    const res = await chatApi.query({
      dsId: selectedDsId.value,
      question,
      conversationId: currentConversationId.value
    })

    if (!currentConversationId.value) {
      currentConversationId.value = res.conversationId
    }

    finishAllSteps()

    const assistantMsg = {
      id: res.historyId || Date.now() + 1,
      role: 'assistant',
      content: res.explanation || (res.status === 'SUCCESS' ? '查询完成' : '查询失败'),
      sql: res.sql,
      data: res.data,
      resultRows: res.data?.length || 0,
      executionTime: res.executionTimeMs,
      error: res.errorMessage
    }
    messages.value.push(assistantMsg)
  } catch (error) {
    finishAllSteps()
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: '查询失败',
      error: error.message || '查询失败，请重试'
    })
  } finally {
    loading.value = false
    cleanupSteps()
    await nextTick()
    scrollToBottom()
  }
}

// --- 初始化 ---
onMounted(async () => {
  await datasourceStore.fetchList()
  await loadConversations()
  if (datasourceStore.list.length > 0) {
    selectedDsId.value = datasourceStore.list[0].id
  }
})
</script>

<style lang="less" scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 100px);
  background: #fff;
  border-radius: 12px;
  overflow: hidden;

  /* ========= 左侧边栏 ========= */
  .sidebar {
    width: 280px;
    border-right: 1px solid #e5e7eb;
    display: flex;
    flex-direction: column;
    background: #f9fafb;

    .sidebar-header {
      padding: 20px;
      h3 { margin: 0 0 16px 0; font-size: 18px; }
    }

    .new-chat-btn { margin: 0 20px 20px; }

    .history-list {
      flex: 1;
      overflow-y: auto;
      padding: 0 10px;

      .history-group {
        margin-bottom: 20px;
        .group-title {
          display: flex; align-items: center; gap: 8px;
          padding: 8px 10px; font-size: 12px; color: #9ca3af;
        }
        .history-item {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 12px 16px; border-radius: 8px; cursor: pointer; margin-bottom: 4px;
          &:hover { background: #e8f4ff; }
          &:hover .item-more { opacity: 1; }
          &.active { background: #eef2ff; border-left: 3px solid #6366f1; }
          .item-content {
            flex: 1;
            min-width: 0;
          }
          .item-time { font-size: 12px; color: #9ca3af; margin-bottom: 4px; }
          .item-title {
            font-size: 14px; color: #1f2937;
            white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
          }
          .item-more {
            opacity: 0;
            flex-shrink: 0;
            font-size: 18px;
            color: #9ca3af;
            padding: 4px;
            border-radius: 4px;
            cursor: pointer;
            transition: all 0.2s;
            &:hover {
              color: #6366f1;
              background: #eef2ff;
            }
          }
        }
      }
    }
  }

  /* ========= 右侧主区域 ========= */
  .main-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;

    /* --- 顶部标题栏 --- */
    .main-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 20px 32px 12px;

      .header-title {
        font-size: 20px;
        font-weight: 700;
        background: linear-gradient(135deg, #6366f1, #8b5cf6);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
      }

      .ds-select { width: 200px; }
    }

    /* --- Tab 栏 --- */
    .tab-bar {
      display: flex;
      gap: 8px;
      padding: 0 32px;
      border-bottom: 1px solid #e5e7eb;

      .tab-item {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 10px 20px;
        cursor: pointer;
        font-size: 14px;
        color: #6b7280;
        border-bottom: 2px solid transparent;
        transition: all 0.2s;
        user-select: none;

        &:hover { color: #6366f1; }

        &.active {
          color: #6366f1;
          font-weight: 600;
          border-bottom-color: #6366f1;
        }

        .tab-dots {
          display: inline-flex; gap: 3px; margin-left: 4px;
          i {
            width: 6px; height: 6px; border-radius: 50%;
            background: #6366f1; display: block;
            animation: tabDot 1.2s infinite;
            &:nth-child(2) { animation-delay: 0.3s; }
          }
        }
      }
    }

    /* --- Tab 内容区 --- */
    .tab-content {
      flex: 1;
      overflow-y: auto;
      padding: 24px 32px;

      .section-title {
        font-size: 15px;
        font-weight: 600;
        color: #1f2937;
        margin-bottom: 12px;
        display: flex;
        align-items: center;
        justify-content: space-between;
      }

      /* 欢迎界面 */
      .welcome-panel {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;

        .welcome-card {
          text-align: center;
          padding: 48px;
          h2 { margin: 20px 0 10px; font-size: 22px; color: #1f2937; }
          p { color: #6b7280; line-height: 1.8; max-width: 400px; }
        }
      }

      /* 对话记录 Tab */
      .history-panel {
        display: flex;
        flex-direction: column;
        gap: 20px;
        padding-bottom: 20px;

        .chat-message {
          display: flex;
          gap: 12px;

          &.user {
            flex-direction: row-reverse;
            .message-body {
              align-items: flex-end;
              .message-text {
                background: linear-gradient(135deg, #6366f1, #8b5cf6);
                color: #fff;
                border-radius: 16px 16px 4px 16px;
              }
            }
          }

          &.assistant {
            .message-body {
              .message-text {
                background: #f3f4f6;
                color: #374151;
                border-radius: 16px 16px 16px 4px;
              }
            }
          }

          .message-avatar {
            flex-shrink: 0;
          }

          .message-body {
            display: flex;
            flex-direction: column;
            max-width: 75%;

            .message-text {
              padding: 12px 16px;
              font-size: 14px;
              line-height: 1.6;
            }

            .message-sql-preview {
              display: flex;
              align-items: center;
              gap: 8px;
              margin-top: 8px;
              padding: 10px 14px;
              background: #1e1e2e;
              border-radius: 8px;
              cursor: pointer;
              transition: all 0.2s;

              &:hover {
                background: #2d2d3d;
              }

              .el-icon {
                color: #a5b4fc;
                flex-shrink: 0;
              }

              code {
                color: #cdd6f4;
                font-size: 12px;
                font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
              }
            }

            .message-meta {
              display: flex;
              align-items: center;
              gap: 12px;
              margin-top: 8px;
              font-size: 12px;
              color: #9ca3af;
            }

            .message-error {
              margin-top: 8px;
            }
          }

          .typing-indicator {
            display: flex;
            gap: 4px;
            padding: 12px 16px;
            background: #f3f4f6;
            border-radius: 16px;

            span {
              width: 8px;
              height: 8px;
              background: #9ca3af;
              border-radius: 50%;
              animation: typing 1.4s infinite;

              &:nth-child(2) { animation-delay: 0.2s; }
              &:nth-child(3) { animation-delay: 0.4s; }
            }
          }
        }
      }

      /* 查询分析 Tab */
      .analysis-panel {
        .analysis-steps {
          margin-bottom: 28px;
          .step-item {
            display: flex;
            align-items: flex-start;
            gap: 12px;
            padding: 12px 0;
            border-left: 2px solid #e5e7eb;
            margin-left: 11px;
            padding-left: 20px;
            position: relative;

            &.done { border-left-color: #10b981; }
            &.loading { border-left-color: #6366f1; }

            .step-icon {
              position: absolute;
              left: -12px;
              top: 10px;
              background: #fff;
              width: 24px; height: 24px;
              display: flex; align-items: center; justify-content: center;
              border-radius: 50%;
            }

            .step-info {
              .step-title { font-size: 14px; font-weight: 500; color: #374151; }
              .step-desc { font-size: 13px; color: #9ca3af; margin-top: 4px; }
            }
          }
        }

        .result-section {
          .result-meta {
            margin-top: 10px;
            font-size: 13px;
            color: #9ca3af;
          }
        }

        .loading-placeholder {
          display: flex;
          justify-content: center;
          gap: 6px;
          padding: 40px 0;
          span {
            width: 8px; height: 8px;
            background: #9ca3af; border-radius: 50%;
            animation: typing 1.4s infinite;
            &:nth-child(2) { animation-delay: 0.2s; }
            &:nth-child(3) { animation-delay: 0.4s; }
          }
        }
      }

      /* SQL语句及解释 Tab */
      .sql-panel {
        .sql-section {
          margin-bottom: 24px;
          .sql-code-block {
            background: #1e1e2e;
            border-radius: 8px;
            overflow: hidden;
            pre {
              margin: 0;
              padding: 20px;
              color: #cdd6f4;
              font-size: 13px;
              line-height: 1.7;
              overflow-x: auto;
              font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace;
            }
          }
        }

        .explanation-section {
          .explanation-text {
            background: #f8fafc;
            border: 1px solid #e2e8f0;
            border-radius: 8px;
            padding: 16px 20px;
            font-size: 14px;
            line-height: 1.8;
            color: #475569;
          }
        }
      }

      /* 可视化图表 Tab */
      .chart-panel {
        .chart-container {
          .chart-toolbar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 16px;
          }
          .echarts-instance {
            width: 100%;
            height: 420px;
          }
        }
      }

      /* 空状态 */
      .empty-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 80px 0;
        p { margin-top: 12px; color: #9ca3af; font-size: 14px; }
      }

      /* 错误信息 */
      .error-section {
        margin-top: 20px;
      }
    }

    /* --- 底部输入区 --- */
    .input-area {
      padding: 16px 32px 20px;
      border-top: 1px solid #e5e7eb;

      .input-wrapper {
        display: flex;
        align-items: center;
        gap: 12px;
        background: #f9fafb;
        border: 1px solid #e5e7eb;
        border-radius: 28px;
        padding: 6px 6px 6px 18px;
        transition: border-color 0.2s;

        &:focus-within {
          border-color: #6366f1;
          box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
        }

        .input-icon { color: #9ca3af; font-size: 18px; flex-shrink: 0; }

        .question-input {
          flex: 1;
          :deep(.el-input__wrapper) {
            box-shadow: none !important;
            background: transparent;
            padding: 0 8px;
          }
        }

        .send-btn {
          flex-shrink: 0;
          min-width: 80px;
        }
      }
    }
  }
}

@keyframes tabDot {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-8px); }
}

/* --- 对话内容区 --- */
.chat-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;

  .welcome-panel {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;

    .welcome-card {
      text-align: center;
      padding: 48px;
      h2 { margin: 20px 0 10px; font-size: 22px; color: #1f2937; }
      p { color: #6b7280; line-height: 1.8; max-width: 400px; }
    }
  }

  .chat-message {
    display: flex;
    gap: 12px;
    margin-bottom: 24px;

    &.user {
      flex-direction: row-reverse;
      .message-body {
        align-items: flex-end;
        .user-message-wrapper {
          display: flex;
          flex-direction: column;
          align-items: flex-end;

          .user-message-text {
            background: linear-gradient(135deg, #6366f1, #8b5cf6);
            color: #fff;
            padding: 12px 18px;
            border-radius: 18px 18px 4px 18px;
            font-size: 14px;
            line-height: 1.6;
            max-width: 600px;
          }

          .user-message-actions {
            margin-top: 4px;
            opacity: 0;
            transition: opacity 0.2s;

            .copy-btn {
              color: #9ca3af;
              padding: 2px 6px;
              font-size: 14px;

              &:hover {
                color: #6366f1;
              }
            }
          }

          &:hover .user-message-actions {
            opacity: 1;
          }
        }
      }
    }

    &.assistant {
      .message-body {
        flex: 1;
        max-width: 100%;
      }
    }

    .message-avatar {
      flex-shrink: 0;
    }

    .message-body {
      display: flex;
      flex-direction: column;
      max-width: 80%;
    }
  }

  /* 助手回复中的功能模块 */
  .response-section {
    background: #f9fafb;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    margin-bottom: 12px;
    overflow: hidden;

    .section-header {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 14px 18px;
      cursor: pointer;
      font-size: 14px;
      font-weight: 600;
      color: #374151;
      background: #fff;
      transition: background 0.2s;

      &:hover {
        background: #f3f4f6;
      }

      .el-icon {
        color: #6366f1;
      }

      .arrow {
        margin-left: auto;
        transition: transform 0.3s;
        color: #9ca3af;

        &.expanded {
          transform: rotate(180deg);
        }
      }
    }

    .section-content {
      padding: 16px 18px;
      border-top: 1px solid #e5e7eb;
    }
  }

  /* 查询分析模块 */
  .analysis-section {
    .mini-steps {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }

    .mini-step {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: #9ca3af;
      padding: 6px 12px;
      background: #fff;
      border-radius: 20px;
      border: 1px solid #e5e7eb;

      &.done {
        color: #10b981;
        border-color: #d1fae5;
        background: #ecfdf5;
        .el-icon { color: #10b981; }
      }

      &.loading {
        color: #6366f1;
        border-color: #c7d2fe;
        background: #eef2ff;
        .el-icon { color: #6366f1; }
      }

      .step-hint {
        font-size: 12px;
        color: #9ca3af;
        margin-left: 4px;
      }
    }

    .analysis-result {
      margin-top: 16px;
      padding: 12px 16px;
      background: #ecfdf5;
      border-radius: 8px;
      color: #065f46;
      font-size: 13px;
    }
  }

  /* SQL语句模块 */
  .sql-section {
    .sql-block {
      background: #f8f9fc;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
      overflow: hidden;

      .sql-toolbar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 10px 16px;
        background: #eef1f6;
        border-bottom: 1px solid #e2e8f0;
        span {
          color: #6366f1;
          font-size: 12px;
          font-weight: 600;
        }
      }

      pre {
        margin: 0;
        padding: 16px;
        overflow-x: auto;

        code {
          color: #1e293b;
          font-size: 13px;
          line-height: 1.6;
          font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace;
        }
      }
    }

    .explanation-block {
      margin-top: 16px;

      .explanation-title {
        font-size: 13px;
        font-weight: 600;
        color: #6b7280;
        margin-bottom: 8px;
      }

      .explanation-text {
        background: #fff;
        border: 1px solid #e5e7eb;
        border-radius: 8px;
        padding: 14px 16px;
        font-size: 13px;
        line-height: 1.8;
        color: #475569;

        :deep(p) {
          margin: 0 0 8px 0;
          &:last-child { margin-bottom: 0; }
        }

        :deep(ul), :deep(ol) {
          margin: 4px 0;
          padding-left: 20px;
        }

        :deep(li) {
          margin-bottom: 4px;
        }

        :deep(code) {
          background: #f1f5f9;
          padding: 1px 5px;
          border-radius: 3px;
          font-size: 12px;
          color: #e11d48;
          font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace;
        }

        :deep(pre) {
          background: #1e1e2e;
          border-radius: 6px;
          padding: 12px;
          overflow-x: auto;
          margin: 8px 0;

          code {
            background: none;
            color: #cdd6f4;
            padding: 0;
          }
        }

        :deep(strong) {
          font-weight: 600;
          color: #374151;
        }

        :deep(table) {
          border-collapse: collapse;
          width: 100%;
          margin: 8px 0;

          th, td {
            border: 1px solid #e5e7eb;
            padding: 6px 10px;
            font-size: 12px;
          }

          th {
            background: #f9fafb;
            font-weight: 600;
          }
        }
      }
    }
  }

  /* 可视化图表模块 */
  .chart-section {
    .chart-wrapper {
      .chart-toolbar {
        margin-bottom: 16px;
      }

      .message-chart {
        width: 100%;
        height: 350px;
      }

      .chart-empty {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 200px;
        color: #9ca3af;
        font-size: 14px;
        background: #fff;
        border-radius: 8px;
        border: 1px dashed #e5e7eb;
      }
    }

    .data-table-wrapper {
      margin-top: 20px;

      .table-title {
        font-size: 13px;
        font-weight: 600;
        color: #6b7280;
        margin-bottom: 10px;
      }
    }
  }

  /* 错误信息 */
  .response-error {
    margin-top: 12px;
  }

  /* 加载中动画 */
  .loading-response {
    .loading-dots {
      display: inline-flex;
      gap: 4px;
      margin-left: 8px;

      i {
        width: 6px;
        height: 6px;
        background: #6366f1;
        border-radius: 50%;
        animation: loadingDot 1.2s infinite;

        &:nth-child(2) { animation-delay: 0.2s; }
      }
    }
  }
}

@keyframes loadingDot {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}
</style>
