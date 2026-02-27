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
        <!-- 今天 -->
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
            <div class="item-time">{{ formatTime(item.createTime) }}</div>
            <div class="item-title">{{ item.title || '新对话' }}</div>
          </div>
        </div>
        
        <!-- 7天内 -->
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
            <div class="item-time">{{ formatTime(item.createTime) }}</div>
            <div class="item-title">{{ item.title || '新对话' }}</div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 右侧对话区域 -->
    <div class="chat-area">
      <!-- 欢迎界面 -->
      <div v-if="!currentConversationId && messages.length === 0" class="welcome-panel">
        <div class="welcome-card">
          <el-icon :size="48" color="#409eff"><ChatDotRound /></el-icon>
          <h2>你好，我是 SQLBot</h2>
          <p>我可以查询数据、生成图表、检测数据异常、预测数据等帮您开启智能问数吧~</p>
          <el-button type="primary" @click="createNewChat">
            <el-icon><Plus /></el-icon>
            开启问数
          </el-button>
        </div>
      </div>
      
      <!-- 对话消息区 -->
      <div v-else class="messages-container" ref="messagesRef">
        <div v-for="msg in messages" :key="msg.id" class="message-item" :class="msg.role">
          <div class="message-avatar">
            <el-avatar v-if="msg.role === 'user'" :size="36">U</el-avatar>
            <el-avatar v-else :size="36" style="background: #409eff">
              <el-icon><DataAnalysis /></el-icon>
            </el-avatar>
          </div>
          <div class="message-content">
            <div class="message-text">{{ msg.content }}</div>
            
            <!-- SQL展示 -->
            <div v-if="msg.sql" class="sql-block">
              <div class="sql-header">
                <span>生成的SQL</span>
                <el-button type="primary" link size="small" @click="copySql(msg.sql)">
                  <el-icon><CopyDocument /></el-icon>
                  复制
                </el-button>
              </div>
              <pre><code>{{ msg.sql }}</code></pre>
            </div>
            
            <!-- 结果表格 -->
            <div v-if="msg.data && msg.data.length > 0" class="result-table">
              <el-table :data="msg.data" max-height="300" border size="small">
                <el-table-column
                  v-for="col in Object.keys(msg.data[0])"
                  :key="col"
                  :prop="col"
                  :label="col"
                  min-width="120"
                />
              </el-table>
              <div class="result-meta">
                共 {{ msg.data.length }} 条结果，耗时 {{ msg.executionTime }}ms
              </div>
            </div>
            
            <!-- 错误信息 -->
            <div v-if="msg.error" class="error-block">
              <el-alert :title="msg.error" type="error" :closable="false" />
            </div>
          </div>
        </div>
        
        <!-- 加载中 -->
        <div v-if="loading" class="message-item assistant">
          <div class="message-avatar">
            <el-avatar :size="36" style="background: #409eff">
              <el-icon><DataAnalysis /></el-icon>
            </el-avatar>
          </div>
          <div class="message-content">
            <div class="typing-indicator">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 输入区域 -->
      <div class="input-area">
        <el-select v-model="selectedDsId" placeholder="选择数据源" style="width: 200px">
          <el-option
            v-for="ds in datasourceStore.list"
            :key="ds.id"
            :label="ds.name"
            :value="ds.id"
          />
        </el-select>
        
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="请输入您的问题，例如：查询近7天的销售总额"
          @keydown.enter.ctrl="sendMessage"
        />
        
        <el-button type="primary" :loading="loading" :disabled="!canSend" @click="sendMessage">
          <el-icon><Promotion /></el-icon>
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useDataSourceStore } from '@/stores/datasource'
import { chatApi } from '@/api/chat'
import { ElMessage } from 'element-plus'
import { Search, Plus, ArrowDown, CopyDocument, Promotion } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

const datasourceStore = useDataSourceStore()

const searchKeyword = ref('')
const inputText = ref('')
const selectedDsId = ref()
const currentConversationId = ref()
const messages = ref([])
const conversations = ref([])
const loading = ref(false)
const messagesRef = ref()

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

const formatTime = (time) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

const loadConversations = async () => {
  try {
    const res = await chatApi.getConversationList()
    conversations.value = res.records || []
  } catch (error) {
    console.error('加载对话列表失败', error)
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
  } catch (error) {
    ElMessage.error(error.message || '创建对话失败')
  }
}

const selectConversation = async (conversation) => {
  currentConversationId.value = conversation.id
  selectedDsId.value = conversation.dsId
  
  try {
    const res = await chatApi.getConversation(conversation.id)
    messages.value = res.histories.flatMap(h => {
      const msgs = [
        { id: h.id * 2, role: 'user', content: h.naturalQuery }
      ]
      if (h.executionStatus === 'SUCCESS') {
        msgs.push({
          id: h.id * 2 + 1,
          role: 'assistant',
          content: '查询完成',
          sql: h.generatedSql,
          executionTime: h.executionTimeMs
        })
      } else {
        msgs.push({
          id: h.id * 2 + 1,
          role: 'assistant',
          content: '查询失败',
          error: '执行出错'
        })
      }
      return msgs
    })
  } catch (error) {
    ElMessage.error(error.message || '加载对话失败')
  }
}

const sendMessage = async () => {
  if (!canSend.value) return
  
  const question = inputText.value.trim()
  inputText.value = ''
  
  // 添加用户消息
  const userMsgId = Date.now()
  messages.value.push({
    id: userMsgId,
    role: 'user',
    content: question
  })
  
  await nextTick()
  scrollToBottom()
  
  loading.value = true
  try {
    const res = await chatApi.query({
      dsId: selectedDsId.value,
      question,
      conversationId: currentConversationId.value
    })
    
    if (!currentConversationId.value) {
      currentConversationId.value = res.conversationId
    }
    
    // 添加助手回复
    messages.value.push({
      id: res.historyId,
      role: 'assistant',
      content: res.status === 'SUCCESS' ? '查询完成' : '查询失败',
      sql: res.sql,
      data: res.data,
      executionTime: res.executionTimeMs,
      error: res.errorMessage
    })
    
    // 刷新对话列表
    loadConversations()
  } catch (error) {
    messages.value.push({
      id: Date.now(),
      role: 'assistant',
      content: '抱歉，查询失败',
      error: error.message || '未知错误'
    })
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

const scrollToBottom = () => {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

const copySql = (sql) => {
  navigator.clipboard.writeText(sql)
  ElMessage.success('SQL已复制到剪贴板')
}

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
  
  .sidebar {
    width: 280px;
    border-right: 1px solid var(--border-color);
    display: flex;
    flex-direction: column;
    background: #f9fafb;
    
    .sidebar-header {
      padding: 20px;
      
      h3 {
        margin: 0 0 16px 0;
        font-size: 18px;
      }
    }
    
    .new-chat-btn {
      margin: 0 20px 20px;
    }
    
    .history-list {
      flex: 1;
      overflow-y: auto;
      padding: 0 10px;
      
      .history-group {
        margin-bottom: 20px;
        
        .group-title {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 8px 10px;
          font-size: 12px;
          color: var(--text-secondary);
        }
        
        .history-item {
          padding: 12px 16px;
          border-radius: 8px;
          cursor: pointer;
          margin-bottom: 4px;
          
          &:hover {
            background: #e8f4ff;
          }
          
          &.active {
            background: var(--primary-light);
            border-left: 3px solid var(--primary-color);
          }
          
          .item-time {
            font-size: 12px;
            color: var(--text-secondary);
            margin-bottom: 4px;
          }
          
          .item-title {
            font-size: 14px;
            color: var(--text-primary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
        }
      }
    }
  }
  
  .chat-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    
    .welcome-panel {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      
      .welcome-card {
        text-align: center;
        padding: 40px;
        background: #f9fafb;
        border-radius: 16px;
        max-width: 400px;
        
        h2 {
          margin: 20px 0 10px;
          font-size: 24px;
        }
        
        p {
          color: var(--text-secondary);
          margin-bottom: 24px;
          line-height: 1.6;
        }
      }
    }
    
    .messages-container {
      flex: 1;
      overflow-y: auto;
      padding: 20px;
      
      .message-item {
        display: flex;
        gap: 12px;
        margin-bottom: 24px;
        
        &.user {
          flex-direction: row-reverse;
          
          .message-content {
            align-items: flex-end;
            
            .message-text {
              background: var(--primary-color);
              color: #fff;
            }
          }
        }
        
        .message-content {
          display: flex;
          flex-direction: column;
          max-width: 80%;
          
          .message-text {
            padding: 12px 16px;
            background: #f0f2f5;
            border-radius: 12px;
            line-height: 1.6;
          }
          
          .sql-block {
            margin-top: 12px;
            background: #1e1e1e;
            border-radius: 8px;
            overflow: hidden;
            
            .sql-header {
              display: flex;
              justify-content: space-between;
              align-items: center;
              padding: 8px 16px;
              background: #2d2d2d;
              color: #fff;
              font-size: 12px;
            }
            
            pre {
              margin: 0;
              padding: 16px;
              color: #d4d4d4;
              font-size: 13px;
              overflow-x: auto;
            }
          }
          
          .result-table {
            margin-top: 12px;
            
            .result-meta {
              margin-top: 8px;
              font-size: 12px;
              color: var(--text-secondary);
            }
          }
          
          .error-block {
            margin-top: 12px;
          }
        }
        
        .typing-indicator {
          display: flex;
          gap: 4px;
          padding: 12px 16px;
          background: #f0f2f5;
          border-radius: 12px;
          
          span {
            width: 8px;
            height: 8px;
            background: var(--text-secondary);
            border-radius: 50%;
            animation: typing 1.4s infinite;
            
            &:nth-child(2) { animation-delay: 0.2s; }
            &:nth-child(3) { animation-delay: 0.4s; }
          }
        }
      }
    }
    
    .input-area {
      padding: 20px;
      border-top: 1px solid var(--border-color);
      display: flex;
      gap: 12px;
      align-items: flex-end;
      
      .el-input {
        flex: 1;
      }
    }
  }
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-8px); }
}
</style>
