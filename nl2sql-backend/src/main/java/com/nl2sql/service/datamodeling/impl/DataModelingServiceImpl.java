package com.nl2sql.service.datamodeling.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.mapper.FieldMetaMapper;
import com.nl2sql.mapper.SyncTaskMapper;
import com.nl2sql.mapper.TableMetaMapper;
import com.nl2sql.model.dto.*;
import com.nl2sql.model.entity.FieldMeta;
import com.nl2sql.model.entity.SyncTask;
import com.nl2sql.model.entity.TableMeta;
import com.nl2sql.repository.neo4j.TableNodeRepository;
import com.nl2sql.service.datamodeling.DataModelingService;
import com.nl2sql.service.llm.DeepSeekProvider;
import com.nl2sql.service.llm.LLMProvider;
import com.nl2sql.service.llm.QwenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataModelingServiceImpl implements DataModelingService {

    @Autowired
    private TableMetaMapper tableMetaMapper;

    @Autowired
    private FieldMetaMapper fieldMetaMapper;

    @Autowired
    private SyncTaskMapper syncTaskMapper;

    @Autowired
    private TableNodeRepository tableNodeRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private DeepSeekProvider deepSeekProvider;

    @Autowired
    private QwenProvider qwenProvider;

    @Value("${nl2sql.llm.provider}")
    private String llmProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ERDiagramDTO getERDiagram(Long dsId) {
        ERDiagramDTO result = new ERDiagramDTO();

        // 获取所有表和字段
        List<TableDetailDTO> tables = getTablesWithFields(dsId);
        result.setTables(tables);

        // 获取AI分析的关系
        List<TableRelationDTO> relations = getRelations(dsId);
        result.setRelations(relations);

        // 判断是否已分析过
        result.setAnalyzed(!relations.isEmpty());

        return result;
    }

    @Override
    public Long analyzeRelationsAsync(Long dsId) {
        // 创建分析任务
        SyncTask task = new SyncTask();
        task.setDsId(dsId);
        task.setTaskType("ANALYZE_RELATION");
        task.setStatus(SyncTask.STATUS_PENDING);
        task.setTotalCount(1);
        task.setCurrentCount(0);
        syncTaskMapper.insert(task);

        // 调用异步方法（需通过代理调用，这里通过Spring上下文）
        executeAnalysisInNewThread(task.getId(), dsId);

        return task.getId();
    }

    /**
     * 在新线程中执行AI分析，避免@Async同类调用问题
     */
    private void executeAnalysisInNewThread(Long taskId, Long dsId) {
        new Thread(() -> {
            try {
                executeAnalysis(taskId, dsId);
            } catch (Exception e) {
                log.error("AI分析关系异常: {}", e.getMessage(), e);
                SyncTask task = syncTaskMapper.selectById(taskId);
                if (task != null) {
                    task.setStatus(SyncTask.STATUS_FAILED);
                    task.setErrorMessage(e.getMessage());
                    syncTaskMapper.updateById(task);
                }
            }
        }).start();
    }

    private void executeAnalysis(Long taskId, Long dsId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        task.setStatus(SyncTask.STATUS_RUNNING);
        task.setCurrentTable("准备分析...");
        syncTaskMapper.updateById(task);

        try {
            // 1. 构建schema文本
            List<TableDetailDTO> tables = getTablesWithFields(dsId);
            if (tables.isEmpty()) {
                task.setStatus(SyncTask.STATUS_FAILED);
                task.setErrorMessage("该数据源没有已同步的表");
                syncTaskMapper.updateById(task);
                return;
            }

            task.setTotalCount(3); // 3个阶段：构建prompt、调用AI、存储结果
            task.setCurrentCount(1);
            task.setCurrentTable("构建Schema...");
            syncTaskMapper.updateById(task);

            String schema = buildSchemaText(tables);
            String prompt = buildAnalysisPrompt(schema);

            // 2. 调用LLM
            task.setCurrentCount(2);
            task.setCurrentTable("AI分析中...");
            syncTaskMapper.updateById(task);

            LLMProvider provider = getLLMProvider();
            String response = provider.generateSql(prompt);
            log.info("AI分析原始响应: {}", response);

            // 3. 解析结果并存储
            task.setCurrentTable("存储分析结果...");
            syncTaskMapper.updateById(task);

            List<Map<String, Object>> relations = parseAnalysisResponse(response);
            
            // 清除旧的AI关系
            tableNodeRepository.deleteAllAIRelationsByDsId(dsId);

            // 建立表名到tableId的映射
            Map<String, Long> tableNameToId = tables.stream()
                    .collect(Collectors.toMap(
                            t -> t.getTableName().toLowerCase(),
                            TableDetailDTO::getTableId,
                            (a, b) -> a
                    ));

            int savedCount = 0;
            for (Map<String, Object> rel : relations) {
                String sourceTable = ((String) rel.get("sourceTable")).toLowerCase();
                String targetTable = ((String) rel.get("targetTable")).toLowerCase();
                
                Long sourceId = tableNameToId.get(sourceTable);
                Long targetId = tableNameToId.get(targetTable);

                if (sourceId == null || targetId == null) {
                    log.warn("AI返回的表名不存在: source={}, target={}", sourceTable, targetTable);
                    continue;
                }

                String relationType = (String) rel.get("relationType");
                Double confidence = rel.get("confidence") instanceof Number 
                        ? ((Number) rel.get("confidence")).doubleValue() : 0.8;
                String reasoning = (String) rel.get("reasoning");

                @SuppressWarnings("unchecked")
                List<String> sourceFields = rel.get("sourceFields") instanceof List 
                        ? (List<String>) rel.get("sourceFields") : List.of();
                @SuppressWarnings("unchecked")
                List<String> targetFields = rel.get("targetFields") instanceof List 
                        ? (List<String>) rel.get("targetFields") : List.of();

                tableNodeRepository.createAIRelation(
                        sourceId, targetId, relationType, confidence, reasoning,
                        String.join(",", sourceFields),
                        String.join(",", targetFields),
                        provider.getName()
                );
                savedCount++;
            }

            log.info("AI分析完成，共保存 {} 条关系", savedCount);

            task.setCurrentCount(3);
            task.setCurrentTable(null);
            task.setStatus(SyncTask.STATUS_SUCCESS);
            syncTaskMapper.updateById(task);

        } catch (Exception e) {
            log.error("AI分析失败: {}", e.getMessage(), e);
            task.setStatus(SyncTask.STATUS_FAILED);
            task.setErrorMessage(e.getMessage());
            syncTaskMapper.updateById(task);
        }
    }

    @Override
    public SyncTask getAnalysisProgress(Long taskId) {
        return syncTaskMapper.selectById(taskId);
    }

    @Override
    public void deleteRelation(Long relationId) {
        tableNodeRepository.deleteAIRelation(relationId);
    }

    @Override
    public void updateFieldComment(Long fieldId, String comment) {
        FieldMeta field = fieldMetaMapper.selectById(fieldId);
        if (field == null) {
            throw new RuntimeException("字段不存在");
        }
        field.setFieldComment(comment);
        fieldMetaMapper.updateById(field);
    }

    @Override
    public void updateRelation(Long relationId, String relationType) {
        neo4jClient.query(
                "MATCH ()-[r:AI_RELATION]->() WHERE id(r) = $relationId " +
                "SET r.relationType = $relationType, r.confidence = 1.0, r.reasoning = '手动设置'"
        )
        .bind(relationId).to("relationId")
        .bind(relationType).to("relationType")
        .run();
    }

    @Override
    public void createRelation(Long dsId, Long sourceTableId, Long targetTableId,
                               String relationType, String sourceFields, String targetFields) {
        tableNodeRepository.createAIRelation(
                sourceTableId, targetTableId, relationType,
                1.0, "手动创建",
                sourceFields != null ? sourceFields : "",
                targetFields != null ? targetFields : "",
                "manual"
        );
    }

    private List<TableDetailDTO> getTablesWithFields(Long dsId) {
        List<TableMeta> tables = tableMetaMapper.selectList(
                new LambdaQueryWrapper<TableMeta>()
                        .eq(TableMeta::getDsId, dsId)
                        .eq(TableMeta::getSyncStatus, 1)
                        .orderByAsc(TableMeta::getTableName));

        List<TableDetailDTO> result = new ArrayList<>();
        for (TableMeta table : tables) {
            TableDetailDTO dto = new TableDetailDTO();
            dto.setTableId(table.getId());
            dto.setTableName(table.getTableName());
            dto.setTableComment(table.getTableComment());

            List<FieldMeta> fields = fieldMetaMapper.selectList(
                    new LambdaQueryWrapper<FieldMeta>()
                            .eq(FieldMeta::getTableId, table.getId())
                            .orderByAsc(FieldMeta::getFieldIndex));

            List<FieldDetailDTO> fieldDTOs = fields.stream().map(f -> {
                FieldDetailDTO fd = new FieldDetailDTO();
                fd.setFieldId(f.getId());
                fd.setFieldName(f.getFieldName());
                fd.setFieldType(f.getFieldType());
                fd.setFieldComment(f.getFieldComment());
                fd.setIsPrimary(f.getIsPrimary() != null && f.getIsPrimary() == 1);
                return fd;
            }).collect(Collectors.toList());

            dto.setFields(fieldDTOs);
            result.add(dto);
        }
        return result;
    }

    private List<TableRelationDTO> getRelations(Long dsId) {
        Collection<Map<String, Object>> rawRelations = neo4jClient
                .query("MATCH (t1:Table {dsId: $dsId})-[r:AI_RELATION]->(t2:Table) " +
                       "RETURN t1.tableId as sourceTableId, t1.tableName as sourceTableName, " +
                       "t2.tableId as targetTableId, t2.tableName as targetTableName, " +
                       "r.relationType as relationType, r.confidence as confidence, " +
                       "r.reasoning as reasoning, r.sourceFields as sourceFields, " +
                       "r.targetFields as targetFields, id(r) as relationId")
                .bind(dsId).to("dsId")
                .fetch()
                .all();

        List<TableRelationDTO> relations = new ArrayList<>();

        for (Map<String, Object> raw : rawRelations) {
            TableRelationDTO dto = new TableRelationDTO();
            dto.setRelationId(toLong(raw.get("relationId")));
            dto.setSourceTableId(toLong(raw.get("sourceTableId")));
            dto.setSourceTableName((String) raw.get("sourceTableName"));
            dto.setTargetTableId(toLong(raw.get("targetTableId")));
            dto.setTargetTableName((String) raw.get("targetTableName"));
            dto.setRelationType((String) raw.get("relationType"));
            dto.setConfidence(raw.get("confidence") instanceof Number
                    ? ((Number) raw.get("confidence")).doubleValue() : null);
            dto.setReasoning((String) raw.get("reasoning"));

            String sf = (String) raw.get("sourceFields");
            dto.setSourceFields(sf != null && !sf.isEmpty() ? Arrays.asList(sf.split(",")) : List.of());
            String tf = (String) raw.get("targetFields");
            dto.setTargetFields(tf != null && !tf.isEmpty() ? Arrays.asList(tf.split(",")) : List.of());

            relations.add(dto);
        }
        return relations;
    }

    private String buildSchemaText(List<TableDetailDTO> tables) {
        StringBuilder sb = new StringBuilder();
        for (TableDetailDTO table : tables) {
            sb.append("表名: ").append(table.getTableName());
            if (table.getTableComment() != null && !table.getTableComment().isEmpty()) {
                sb.append(" (").append(table.getTableComment()).append(")");
            }
            sb.append("\n字段:\n");

            for (FieldDetailDTO field : table.getFields()) {
                sb.append("  - ").append(field.getFieldName())
                  .append(" ").append(field.getFieldType());
                if (Boolean.TRUE.equals(field.getIsPrimary())) {
                    sb.append(" [主键]");
                }
                if (field.getFieldComment() != null && !field.getFieldComment().isEmpty()) {
                    sb.append(" -- ").append(field.getFieldComment());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildAnalysisPrompt(String schema) {
        String template;
        try {
            ClassPathResource resource = new ClassPathResource("prompts/analyze_relations.txt");
            template = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("加载分析prompt模板失败，使用默认模板");
            template = getDefaultPromptTemplate();
        }
        return template.replace("{{schema}}", schema);
    }

    private String getDefaultPromptTemplate() {
        return """
                你是一个数据库建模专家。请分析以下数据库表之间的关系。
                
                数据库表结构:
                {{schema}}
                
                请分析表之间可能存在的关系，输出JSON数组格式:
                [{"sourceTable":"表名1","targetTable":"表名2","relationType":"ONE_TO_MANY","confidence":0.95,"reasoning":"推断依据","sourceFields":["id"],"targetFields":["user_id"]}]
                
                分析规则:
                1. 识别字段命名规律(如 user_id 指向 user.id)
                2. relationType取值: ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY
                3. confidence: 0-1之间的置信度
                4. 只返回confidence>0.7的关系
                5. 只返回JSON数组，不要markdown标记或其他文字
                """;
    }

    private List<Map<String, Object>> parseAnalysisResponse(String response) {
        String json = response.trim();

        // 清理markdown标记
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();

        // 提取JSON数组部分
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("解析AI响应JSON失败: {}", e.getMessage());
            log.error("原始响应: {}", response);
            throw new RuntimeException("AI返回的关系数据格式无法解析");
        }
    }

    private LLMProvider getLLMProvider() {
        return switch (llmProvider.toLowerCase()) {
            case "qwen" -> qwenProvider;
            default -> deepSeekProvider;
        };
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }
}
