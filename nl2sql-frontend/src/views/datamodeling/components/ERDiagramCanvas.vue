<template>
  <div class="er-diagram-canvas" v-loading="loading" element-loading-text="加载中...">
    <div v-if="!hasTables && !loading" class="empty-state">
      <el-empty description="请从左侧选择数据源查看ER图" :image-size="120" />
    </div>
    <div ref="containerRef" class="graph-container" v-show="hasTables"></div>
    
    <!-- 表格详情弹窗（可编辑字段注释） -->
    <el-dialog
      v-model="showTableDetailDialog"
      :title="selectedTable?.tableName || '表详情'"
      width="700px"
      destroy-on-close
    >
      <div class="table-detail-content">
        <div class="table-meta">
          <p><strong>表名:</strong> {{ selectedTable?.tableName }}</p>
          <p v-if="selectedTable?.tableComment"><strong>备注:</strong> {{ selectedTable?.tableComment }}</p>
          <p><strong>字段数量:</strong> {{ selectedTable?.fields?.length || 0 }}</p>
        </div>
        
        <div class="field-list">
          <div class="field-list-header">
            <h4>字段列表</h4>
            <el-button v-if="!editingFields" type="primary" text size="small" @click="startEditFields">
              <el-icon><Edit /></el-icon> 编辑注释
            </el-button>
            <div v-else>
              <el-button type="primary" size="small" @click="saveFieldComments" :loading="savingComments">保存</el-button>
              <el-button size="small" @click="cancelEditFields">取消</el-button>
            </div>
          </div>
          <el-table :data="editingFields ? editableFields : selectedTable?.fields" style="width: 100%; margin-top: 10px;">
            <el-table-column prop="fieldName" label="字段名" width="160" />
            <el-table-column prop="fieldType" label="类型" width="120" />
            <el-table-column label="主键" width="60">
              <template #default="{ row }">
                <el-tag v-if="row.isPrimary" type="warning" size="small">PK</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="注释">
              <template #default="{ row }">
                <el-input
                  v-if="editingFields"
                  v-model="row.fieldComment"
                  size="small"
                  placeholder="输入字段注释"
                />
                <span v-else>{{ row.fieldComment || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>

    <!-- 关系详情弹窗（可编辑） -->
    <el-dialog
      v-model="showRelationDialog"
      title="表关系详情"
      width="500px"
      destroy-on-close
    >
      <div class="relation-detail-content" v-if="selectedRelation">
        <div class="relation-type-badge">
          <el-select
            v-if="editingRelation"
            v-model="editRelationType"
            style="width: 200px;"
          >
            <el-option label="一对一 (1:1)" value="ONE_TO_ONE" />
            <el-option label="一对多 (1:N)" value="ONE_TO_MANY" />
            <el-option label="多对一 (N:1)" value="MANY_TO_ONE" />
            <el-option label="多对多 (N:N)" value="MANY_TO_MANY" />
          </el-select>
          <el-tag v-else :type="getRelationTagType(selectedRelation.relationType)" size="large" effect="dark">
            {{ getRelationTypeLabel(selectedRelation.relationType) }}
          </el-tag>
        </div>
        
        <div class="relation-tables">
          <div class="table-box source">
            <div class="table-label">源表</div>
            <div class="table-name">{{ selectedRelation.sourceTableName }}</div>
            <div class="table-fields" v-if="selectedRelation.sourceFields?.length">
              <span class="field-label">关联字段:</span>
              <el-tag v-for="field in selectedRelation.sourceFields" :key="field" size="small" type="info">
                {{ field }}
              </el-tag>
            </div>
          </div>
          
          <div class="relation-arrow">
            <el-icon :size="24"><Right /></el-icon>
          </div>
          
          <div class="table-box target">
            <div class="table-label">目标表</div>
            <div class="table-name">{{ selectedRelation.targetTableName }}</div>
            <div class="table-fields" v-if="selectedRelation.targetFields?.length">
              <span class="field-label">关联字段:</span>
              <el-tag v-for="field in selectedRelation.targetFields" :key="field" size="small" type="info">
                {{ field }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="relation-info">
          <div class="info-item" v-if="selectedRelation.confidence">
            <span class="info-label">置信度:</span>
            <el-progress 
              :percentage="Math.round(selectedRelation.confidence * 100)" 
              :color="getConfidenceColor(selectedRelation.confidence)"
              :stroke-width="12"
              style="width: 200px; display: inline-flex; margin-left: 8px;"
            />
          </div>
          <div class="info-item" v-if="selectedRelation.reasoning">
            <span class="info-label">推断依据:</span>
            <p class="reasoning-text">{{ selectedRelation.reasoning }}</p>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="relation-dialog-footer">
          <el-button type="danger" text @click="handleDeleteRelation" :loading="deletingRelation">
            <el-icon><Delete /></el-icon> 删除关系
          </el-button>
          <div>
            <template v-if="editingRelation">
              <el-button @click="editingRelation = false">取消</el-button>
              <el-button type="primary" @click="handleSaveRelation" :loading="savingRelation">保存</el-button>
            </template>
            <el-button v-else type="primary" @click="startEditRelation">
              <el-icon><Edit /></el-icon> 修改关系
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 新增关系弹窗 -->
    <el-dialog
      v-model="showAddRelationDialog"
      title="新增表关系"
      width="560px"
      destroy-on-close
    >
      <el-form :model="newRelation" label-width="80px">
        <el-form-item label="源表">
          <el-select v-model="newRelation.sourceTableId" placeholder="选择源表" filterable style="width: 100%;">
            <el-option
              v-for="t in tables"
              :key="t.tableId"
              :label="t.tableName"
              :value="t.tableId"
            >
              <span>{{ t.tableName }}</span>
              <span v-if="t.tableComment" style="color: #999; margin-left: 8px; font-size: 12px;">{{ t.tableComment }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="源字段">
          <el-select
            v-model="newRelation.sourceFieldList"
            placeholder="选择关联字段"
            multiple
            filterable
            style="width: 100%;"
            :disabled="!newRelation.sourceTableId"
          >
            <el-option
              v-for="f in sourceTableFields"
              :key="f.fieldName"
              :label="f.fieldName"
              :value="f.fieldName"
            >
              <span>{{ f.fieldName }}</span>
              <span style="color: #999; margin-left: 8px; font-size: 12px;">{{ f.fieldType }}</span>
              <el-tag v-if="f.isPrimary" type="warning" size="small" style="margin-left: 4px;">PK</el-tag>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="关系类型">
          <el-select v-model="newRelation.relationType" placeholder="选择关系类型" style="width: 100%;">
            <el-option label="一对一 (1:1)" value="ONE_TO_ONE" />
            <el-option label="一对多 (1:N)" value="ONE_TO_MANY" />
            <el-option label="多对一 (N:1)" value="MANY_TO_ONE" />
            <el-option label="多对多 (N:N)" value="MANY_TO_MANY" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标表">
          <el-select v-model="newRelation.targetTableId" placeholder="选择目标表" filterable style="width: 100%;">
            <el-option
              v-for="t in tables"
              :key="t.tableId"
              :label="t.tableName"
              :value="t.tableId"
            >
              <span>{{ t.tableName }}</span>
              <span v-if="t.tableComment" style="color: #999; margin-left: 8px; font-size: 12px;">{{ t.tableComment }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="目标字段">
          <el-select
            v-model="newRelation.targetFieldList"
            placeholder="选择关联字段"
            multiple
            filterable
            style="width: 100%;"
            :disabled="!newRelation.targetTableId"
          >
            <el-option
              v-for="f in targetTableFields"
              :key="f.fieldName"
              :label="f.fieldName"
              :value="f.fieldName"
            >
              <span>{{ f.fieldName }}</span>
              <span style="color: #999; margin-left: 8px; font-size: 12px;">{{ f.fieldType }}</span>
              <el-tag v-if="f.isPrimary" type="warning" size="small" style="margin-left: 4px;">PK</el-tag>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddRelationDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateRelation" :loading="creatingRelation">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Graph, Shape } from '@antv/x6'
import dagre from '@dagrejs/dagre'
import { ElMessage, ElPopover, ElMessageBox } from 'element-plus'
import { Right, Edit, Delete } from '@element-plus/icons-vue'
import { datamodelingApi } from '@/api/datamodeling'

const props = defineProps({
  tables: { type: Array, default: () => [] },
  relations: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  dsId: { type: [Number, String], default: null }
})

const emit = defineEmits(['refresh'])

const containerRef = ref(null)
let graph = null
const expandedTables = ref(new Set())
const showTableDetailDialog = ref(false)
const selectedTable = ref(null)
const showRelationDialog = ref(false)
const selectedRelation = ref(null)

// 字段注释编辑
const editingFields = ref(false)
const editableFields = ref([])
const savingComments = ref(false)

// 关系编辑
const editingRelation = ref(false)
const editRelationType = ref('')
const savingRelation = ref(false)
const deletingRelation = ref(false)

// 新增关系
const showAddRelationDialog = ref(false)
const creatingRelation = ref(false)
const newRelation = ref({
  sourceTableId: null,
  targetTableId: null,
  relationType: 'ONE_TO_MANY',
  sourceFieldList: [],
  targetFieldList: []
})

// 根据选中的表动态获取字段列表
const sourceTableFields = computed(() => {
  if (!newRelation.value.sourceTableId) return []
  const t = props.tables.find(t => t.tableId === newRelation.value.sourceTableId)
  return t?.fields || []
})

const targetTableFields = computed(() => {
  if (!newRelation.value.targetTableId) return []
  const t = props.tables.find(t => t.tableId === newRelation.value.targetTableId)
  return t?.fields || []
})

const hasTables = computed(() => props.tables.length > 0)

// 关系类型标签映射
const relationLabelMap = {
  'ONE_TO_ONE': '1 : 1',
  'ONE_TO_MANY': '1 : N',
  'MANY_TO_ONE': 'N : 1',
  'MANY_TO_MANY': 'N : N'
}

// 关系类型颜色映射
const relationColorMap = {
  'ONE_TO_ONE': '#67C23A',
  'ONE_TO_MANY': '#409EFF',
  'MANY_TO_ONE': '#E6A23C',
  'MANY_TO_MANY': '#F56C6C'
}

// 注册自定义 HTML 表节点
let tableNodeRegistered = false
const registerTableNode = () => {
  if (tableNodeRegistered) return
  
  Shape.HTML.register({
    shape: 'table-node',
    width: 264,
    height: 100,
    effect: ['data'],
    html(cell) {
      const data = cell.getData() || {}
      const { table, isExpanded, escapeHtmlFn } = data
      
      if (!table) {
        const emptyDiv = document.createElement('div')
        emptyDiv.innerHTML = '<div style="padding:20px;color:#999;">加载中...</div>'
        return emptyDiv
      }

      const escapeHtml = escapeHtmlFn || ((text) => {
        if (!text) return ''
        return text
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;')
      })

      const fields = table.fields || []
      const maxFieldsDisplay = isExpanded ? fields.length : 15
      const displayFields = fields.slice(0, maxFieldsDisplay)
      const hasMore = fields.length > maxFieldsDisplay
      const showExpandButton = fields.length > 15

      // 构建字段HTML
      const fieldsHtml = displayFields.map((f, idx) => {
        const pkIcon = f.isPrimary ? '<span style="color:#FF0000;margin-right:4px;font-weight:bold;">PK</span>' : ''
        const fkIcon = f.isForeign ? '<span style="color:#0000FF;margin-right:4px;font-weight:bold;">FK</span>' : ''
        const typeText = f.fieldType || ''
        const commentText = f.fieldComment ? `<br/><span style="color:#888888;font-size:10px;margin-left:20px;">${escapeHtml(f.fieldComment)}</span>` : ''
        const bgColor = idx % 2 === 0 ? '#FAFAFA' : '#FFFFFF'
        return `<div style="display:flex;align-items:flex-start;padding:6px 12px;font-size:12px;background:${bgColor};border-bottom:1px solid #E0E0E0;">
          <span style="flex:1;color:#333333;white-space:normal;word-break:break-all;min-width:0;">${pkIcon}${fkIcon}${escapeHtml(f.fieldName)}${commentText}</span>
          <span style="color:#666666;font-size:11px;margin-left:8px;white-space:nowrap;flex-shrink:0;">${escapeHtml(typeText)}</span>
        </div>`
      }).join('')

      const moreHtml = hasMore
        ? `<div style="padding:4px 12px;font-size:11px;color:#999999;text-align:center;">... 还有 ${fields.length - maxFieldsDisplay} 个字段</div>`
        : ''

      const expandButtonHtml = showExpandButton
        ? `<div class="expand-btn" data-table-id="${table.tableId}" style="padding:6px 12px;text-align:center;border-top:1px solid #E0E0E0;cursor:pointer;background:#f8f9fc;">
            <span style="color:#333333;font-size:12px;font-weight:500;">${isExpanded ? '收起字段 ▲' : '展开全部字段 ▼'}</span>
          </div>`
        : ''

      const statsHtml = `<div style="padding:6px 12px;display:flex;justify-content:space-between;font-size:11px;color:#666666;border-bottom:1px solid #E0E0E0;">
        <span>字段: ${fields.length}</span>
        <span>Pk: ${fields.filter(f => f.isPrimary).length}</span>
      </div>`

      const commentLine = table.tableComment
        ? `<div style="font-size:10px;opacity:0.9;margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(table.tableComment)}</div>`
        : ''

      const div = document.createElement('div')
      div.innerHTML = `<div class="table-node-container" data-table-id="${table.tableId}" style="border:2px solid #333333;border-radius:8px;overflow:hidden;box-shadow:0 2px 12px rgba(0, 0, 0, 0.1);background:#fff;width:260px;cursor:pointer;transition:all 0.3s;">
        <div style="background:#333333;color:#fff;padding:12px 15px;">
          <div style="font-size:15px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(table.tableName)}</div>
          ${commentLine}
        </div>
        ${statsHtml}
        <div style="max-height:400px;overflow-y:auto;">
          ${fieldsHtml}
          ${moreHtml}
        </div>
        ${expandButtonHtml}
      </div>`
      
      // 添加鼠标悬停效果
      const container = div.querySelector('.table-node-container')
      if (container) {
        container.addEventListener('mouseenter', () => {
          container.style.borderColor = '#000000'
          container.style.boxShadow = '0 4px 16px rgba(0, 0, 0, 0.2)'
          container.style.transform = 'scale(1.02)'
        })
        container.addEventListener('mouseleave', () => {
          container.style.borderColor = '#333333'
          container.style.boxShadow = '0 2px 12px rgba(0, 0, 0, 0.1)'
          container.style.transform = 'scale(1)'
        })
      }

      return div
    },
  })
  
  tableNodeRegistered = true
}

const initGraph = () => {
  if (!containerRef.value || graph) return

  // 注册自定义节点
  registerTableNode()

  graph = new Graph({
    container: containerRef.value,
    autoResize: true,
    panning: {
      enabled: true,
      modifiers: [],
    },
    mousewheel: {
      enabled: true,
      modifiers: [],
      zoomAtMousePosition: true,
      minScale: 0.3,
      maxScale: 3,
    },
    background: { color: '#FFFFFF' }, // 白色背景
    grid: {
      visible: true,
      type: 'dot',
      args: {
        color: '#E0E0E0',
        thickness: 1,
      },
      size: 20,
    },
    connecting: {
      anchor: 'center',
      connectionPoint: 'anchor',
    },
    interacting: {
      nodeMovable: true,
      edgeMovable: false,
      edgeLabelMovable: false,
    },
  })
  
  // 添加点击事件监听器 - 处理展开按钮点击
  graph.on('node:click', ({ e, node }) => {
    const target = e.target
    // 检查是否点击了展开按钮
    if (target && (target.classList?.contains('expand-btn') || target.closest?.('.expand-btn'))) {
      const tableId = node.getData().tableId
      if (tableId) {
        toggleExpandTable(tableId)
      }
    }
  })
  
  // 双击查看表详情
  graph.on('node:dblclick', ({ node }) => {
    const data = node.getData()
    if (data && data.table) {
      showTableDetail(data.table)
    }
  })

  // 点击边查看关系详情
  graph.on('edge:click', ({ edge }) => {
    const data = edge.getData()
    if (data) {
      // 获取源表和目标表名称
      const sourceNode = edge.getSourceNode()
      const targetNode = edge.getTargetNode()
      const sourceData = sourceNode?.getData()
      const targetData = targetNode?.getData()
      
      selectedRelation.value = {
        ...data,
        sourceTableName: sourceData?.table?.tableName || '未知',
        targetTableName: targetData?.table?.tableName || '未知',
      }
      showRelationDialog.value = true
    }
  })
}

const renderDiagram = () => {
  if (!graph) return
  graph.clearCells()

  if (props.tables.length === 0) return

  const nodeMap = {}

  // 创建表节点
  props.tables.forEach((table) => {
    const fields = table.fields || []
    const isExpanded = isTableExpanded(table.tableId)
    const maxFieldsDisplay = isExpanded ? fields.length : 15
    const displayFields = fields.slice(0, maxFieldsDisplay)
    const hasMore = fields.length > maxFieldsDisplay
    const showExpandButton = fields.length > 15

    // 计算节点高度
    const headerHeight = 40
    const fieldHeight = 26
    const moreHeight = hasMore ? 24 : 0
    const expandBtnHeight = showExpandButton ? 28 : 0
    const statsHeight = 30
    const totalHeight = headerHeight + statsHeight + displayFields.length * fieldHeight + moreHeight + expandBtnHeight + 8

    // 将表数据存储到全局变量中，通过 tableId 索引
    if (!window._tableDataMap) window._tableDataMap = {}
    window._tableDataMap[table.tableId] = table

    const node = graph.addNode({
      shape: 'table-node',
      x: 0,
      y: 0,
      width: 264,
      height: totalHeight,
      data: { 
        tableId: table.tableId,
        table: table,
        isExpanded: isExpanded,
        escapeHtmlFn: escapeHtml
      },
    })

    nodeMap[table.tableId] = node
  })

  // 创建关系连线
  props.relations.forEach((rel) => {
    const sourceNode = nodeMap[rel.sourceTableId]
    const targetNode = nodeMap[rel.targetTableId]
    if (!sourceNode || !targetNode) return

    const label = relationLabelMap[rel.relationType] || rel.relationType
    const color = relationColorMap[rel.relationType] || '#409EFF'

    graph.addEdge({
      source: sourceNode,
      target: targetNode,
      router: { name: 'er', args: { offset: 'center' } },
      connector: { name: 'rounded', args: { radius: 10 } },
      labels: [
        {
          position: 0.5,
          attrs: {
            text: {
              text: label,
              fill: color,
              fontSize: 11,
              fontWeight: 'bold',
            },
            rect: {
              fill: '#fff',
              stroke: color,
              strokeWidth: 1,
              rx: 4,
              ry: 4,
              refWidth: '140%',
              refHeight: '140%',
              refX: '-20%',
              refY: '-20%',
            },
          },
        },
      ],
      attrs: {
        line: {
          stroke: color,
          strokeWidth: 2,
          strokeDasharray: rel.confidence && rel.confidence < 0.85 ? '5 5' : '',
          targetMarker: {
            name: 'classic',
            size: 8,
          },
        },
      },
      data: {
        relationId: rel.relationId,
        relationType: rel.relationType,
        reasoning: rel.reasoning,
        confidence: rel.confidence,
        sourceFields: rel.sourceFields || [],
        targetFields: rel.targetFields || [],
      },
    })
  })

  // 自动布局
  autoLayout()
}

const autoLayout = () => {
  if (!graph) return

  const nodes = graph.getNodes()
  const edges = graph.getEdges()

  if (nodes.length === 0) return

  const layoutData = {
    nodes: nodes.map((node) => ({
      id: node.id,
      size: { width: node.getSize().width, height: node.getSize().height },
    })),
    edges: edges.map((edge) => ({
      source: edge.getSourceCellId(),
      target: edge.getTargetCellId(),
    })),
  }

  const g = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: 'LR', nodesep: 60, ranksep: 120, align: 'UL' });

  // 添加节点到图中
  layoutData.nodes.forEach(node => {
    g.setNode(node.id, { width: node.size.width, height: node.size.height });
  });

  // 添加边到图中
  layoutData.edges.forEach(edge => {
    g.setEdge(edge.source, edge.target);
  });

  // 执行布局
  dagre.layout(g);

  // 更新节点位置
  layoutData.nodes.forEach(node => {
    const layoutNode = g.node(node.id);
    const cell = graph.getCellById(node.id);
    if (cell && layoutNode) {
      cell.setPosition(layoutNode.x - layoutNode.width / 2, layoutNode.y - layoutNode.height / 2);
    }
  });

  // 适应画布
  nextTick(() => {
    if (graph) {
      graph.zoomToFit({ padding: 40, maxScale: 1.2 })
      graph.centerContent()
    }
  })
}

const fitView = () => {
  if (graph) {
    graph.zoomToFit({ padding: 40, maxScale: 1.2 })
    graph.centerContent()
  }
}

const escapeHtml = (text) => {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

const toggleExpandTable = (tableId) => {
  if (expandedTables.value.has(tableId)) {
    expandedTables.value.delete(tableId)
  } else {
    expandedTables.value.add(tableId)
  }
  // 重新渲染图表以反映展开状态
  nextTick(() => renderDiagram())
}

const isTableExpanded = (tableId) => {
  return expandedTables.value.has(tableId)
}

const showTableDetail = (table) => {
  selectedTable.value = table
  editingFields.value = false
  showTableDetailDialog.value = true
}

const hideTableDetail = () => {
  showTableDetailDialog.value = false
  selectedTable.value = null
  editingFields.value = false
}

// === 字段注释编辑 ===
const startEditFields = () => {
  editableFields.value = (selectedTable.value?.fields || []).map(f => ({ ...f }))
  editingFields.value = true
}

const cancelEditFields = () => {
  editingFields.value = false
  editableFields.value = []
}

const saveFieldComments = async () => {
  savingComments.value = true
  try {
    const original = selectedTable.value?.fields || []
    const promises = []
    for (let i = 0; i < editableFields.value.length; i++) {
      const edited = editableFields.value[i]
      const orig = original[i]
      if (edited.fieldComment !== (orig.fieldComment || '')) {
        promises.push(datamodelingApi.updateFieldComment(edited.fieldId, edited.fieldComment || ''))
      }
    }
    if (promises.length > 0) {
      await Promise.all(promises)
      ElMessage.success(`已更新 ${promises.length} 个字段注释`)
      emit('refresh')
    } else {
      ElMessage.info('没有修改')
    }
    editingFields.value = false
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    savingComments.value = false
  }
}

// === 关系编辑 ===
const startEditRelation = () => {
  editRelationType.value = selectedRelation.value.relationType
  editingRelation.value = true
}

const handleSaveRelation = async () => {
  savingRelation.value = true
  try {
    await datamodelingApi.updateRelation(selectedRelation.value.relationId, editRelationType.value)
    ElMessage.success('关系已更新')
    editingRelation.value = false
    showRelationDialog.value = false
    emit('refresh')
  } catch (error) {
    ElMessage.error(error.message || '更新失败')
  } finally {
    savingRelation.value = false
  }
}

const handleDeleteRelation = async () => {
  try {
    await ElMessageBox.confirm('确定要删除这条关系吗？', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  
  deletingRelation.value = true
  try {
    await datamodelingApi.deleteRelation(selectedRelation.value.relationId)
    ElMessage.success('关系已删除')
    showRelationDialog.value = false
    emit('refresh')
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  } finally {
    deletingRelation.value = false
  }
}

// === 新增关系 ===
const openAddRelationDialog = () => {
  newRelation.value = {
    sourceTableId: null,
    targetTableId: null,
    relationType: 'ONE_TO_MANY',
    sourceFieldList: [],
    targetFieldList: []
  }
  showAddRelationDialog.value = true
}

const handleCreateRelation = async () => {
  if (!newRelation.value.sourceTableId || !newRelation.value.targetTableId) {
    ElMessage.warning('请选择源表和目标表')
    return
  }
  if (newRelation.value.sourceTableId === newRelation.value.targetTableId) {
    ElMessage.warning('源表和目标表不能相同')
    return
  }
  
  creatingRelation.value = true
  try {
    await datamodelingApi.createRelation(props.dsId, {
      sourceTableId: newRelation.value.sourceTableId,
      targetTableId: newRelation.value.targetTableId,
      relationType: newRelation.value.relationType,
      sourceFields: newRelation.value.sourceFieldList.join(','),
      targetFields: newRelation.value.targetFieldList.join(',')
    })
    ElMessage.success('关系创建成功')
    showAddRelationDialog.value = false
    emit('refresh')
  } catch (error) {
    ElMessage.error(error.message || '创建失败')
  } finally {
    creatingRelation.value = false
  }
}

// 关系类型标签
const getRelationTypeLabel = (type) => {
  const labels = {
    'ONE_TO_ONE': '一对一 (1:1)',
    'ONE_TO_MANY': '一对多 (1:N)',
    'MANY_TO_ONE': '多对一 (N:1)',
    'MANY_TO_MANY': '多对多 (N:N)'
  }
  return labels[type] || type
}

// 关系类型对应的标签颜色
const getRelationTagType = (type) => {
  const types = {
    'ONE_TO_ONE': 'success',
    'ONE_TO_MANY': 'primary',
    'MANY_TO_ONE': 'warning',
    'MANY_TO_MANY': 'danger'
  }
  return types[type] || 'info'
}

// 置信度颜色
const getConfidenceColor = (confidence) => {
  if (confidence >= 0.9) return '#67C23A'
  if (confidence >= 0.7) return '#409EFF'
  if (confidence >= 0.5) return '#E6A23C'
  return '#F56C6C'
}

// 监听数据变化
watch(
  () => [props.tables, props.relations],
  () => {
    if (hasTables.value) {
      nextTick(() => renderDiagram())
    }
  },
  { deep: true }
)

onMounted(() => {
  nextTick(() => {
    initGraph()
    if (hasTables.value) {
      renderDiagram()
    }
  })
})

onBeforeUnmount(() => {
  if (graph) {
    graph.dispose()
    graph = null
  }
  
  // 清除全局数据
  delete window._tableDataMap
})

defineExpose({ fitView, openAddRelationDialog })
</script>

<style lang="less" scoped>
.er-diagram-canvas {
  flex: 1;
  position: relative;
  overflow: hidden;

  .empty-state {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .graph-container {
    width: 100%;
    height: 100%;
  }
}

.table-detail-content {
  .table-meta {
    margin-bottom: 20px;
    
    p {
      margin: 8px 0;
      font-size: 14px;
      
      strong {
        color: #333333;
      }
    }
  }
  
  .field-list {
    .field-list-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      
      h4 {
        margin: 0;
        color: #333333;
        font-size: 16px;
        font-weight: 600;
      }
    }
  }
}

.relation-detail-content {
  .relation-type-badge {
    text-align: center;
    margin-bottom: 24px;
    
    .el-tag {
      font-size: 16px;
      padding: 8px 20px;
    }
  }
  
  .relation-tables {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    margin-bottom: 24px;
    
    .table-box {
      flex: 1;
      max-width: 180px;
      padding: 16px;
      border-radius: 8px;
      text-align: center;
      
      &.source {
        background: linear-gradient(135deg, #e8f4fd 0%, #d1e9fc 100%);
        border: 1px solid #b3d8fb;
      }
      
      &.target {
        background: linear-gradient(135deg, #e8fdf4 0%, #d1fcec 100%);
        border: 1px solid #b3fbd8;
      }
      
      .table-label {
        font-size: 12px;
        color: #666;
        margin-bottom: 4px;
      }
      
      .table-name {
        font-size: 15px;
        font-weight: 600;
        color: #333;
        word-break: break-all;
      }
      
      .table-fields {
        margin-top: 10px;
        
        .field-label {
          display: block;
          font-size: 11px;
          color: #999;
          margin-bottom: 4px;
        }
        
        .el-tag {
          margin: 2px;
        }
      }
    }
    
    .relation-arrow {
      color: #409EFF;
      flex-shrink: 0;
    }
  }
  
  .relation-info {
    background: #f8f9fa;
    padding: 16px;
    border-radius: 8px;
    
    .info-item {
      margin-bottom: 12px;
      
      &:last-child {
        margin-bottom: 0;
      }
      
      .info-label {
        font-weight: 500;
        color: #333;
        margin-right: 8px;
      }
      
      .reasoning-text {
        margin: 8px 0 0 0;
        padding: 10px;
        background: #fff;
        border-radius: 4px;
        font-size: 13px;
        color: #666;
        line-height: 1.6;
      }
    }
  }
}

.relation-dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>