package com.nl2sql.service.nl2sql.impl;

import com.nl2sql.model.graph.FieldNode;
import com.nl2sql.model.graph.TableNode;
import com.nl2sql.repository.neo4j.FieldNodeRepository;
import com.nl2sql.repository.neo4j.TableNodeRepository;
import com.nl2sql.service.graph.Neo4jService;
import com.nl2sql.service.nl2sql.SchemaContextService;
import com.nl2sql.service.vector.SchemaSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Schema 上下文服务实现
 * 提供全量Schema和精简Schema的构建功能
 */
@Slf4j
@Service
public class SchemaContextServiceImpl implements SchemaContextService {

    @Autowired
    private TableNodeRepository tableNodeRepository;

    @Autowired
    private FieldNodeRepository fieldNodeRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private SchemaSearchService schemaSearchService;

    @Value("${nl2sql.vector.topk:10}")
    private int defaultTopK;

    @Value("${nl2sql.vector.field-topk:5}")
    private int defaultFieldTopK;

    @Value("${nl2sql.schema.max-tables:50}")
    private int maxTables;

    @Value("${nl2sql.schema.max-fields-per-table:30}")
    private int maxFieldsPerTable;

    @Override
    public String buildFullSchemaContext(Long dsId) {
        log.info("构建全量 Schema 上下文，数据源ID: {}", dsId);

        try {
            List<TableNode> tables = tableNodeRepository.findAllTablesByDsId(dsId);
            if (tables == null || tables.isEmpty()) {
                log.warn("数据源 {} 没有找到任何表", dsId);
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# 数据库 Schema 信息\n\n");

            // 限制表数量
            int tableCount = Math.min(tables.size(), maxTables);
            sb.append(String.format("共 %d 张表（显示前 %d 张）：\n\n", tables.size(), tableCount));

            for (int i = 0; i < tableCount; i++) {
                TableNode table = tables.get(i);
                appendTableInfo(sb, table, true); // true = 显示所有字段
            }

            // 添加表关系
            appendTableRelations(sb, dsId);

            log.info("全量 Schema 构建完成，共 {} 张表", tableCount);
            return sb.toString();

        } catch (Exception e) {
            log.error("构建全量 Schema 失败: {}", e.getMessage(), e);
            return "";
        }
    }

    @Override
    public String buildCompactSchemaContext(String question, Long dsId) {
        log.info("构建精简 Schema 上下文，查询: '{}', 数据源ID: {}", question, dsId);

        try {
            // 1. 使用向量检索获取相关表
            SchemaSearchService.TableFieldSearchResult searchResult =
                    schemaSearchService.searchRelevantTablesAndFields(
                            question, dsId, defaultTopK, defaultFieldTopK);

            List<SchemaSearchService.TableSearchResult> relevantTables = searchResult.getTables();

            if (relevantTables == null || relevantTables.isEmpty()) {
                log.warn("向量检索未找到相关表，回退到全量Schema");
                return buildFullSchemaContext(dsId);
            }

            // 2. 添加关联表（通过外键关系）
            Set<Long> allTableIds = new LinkedHashSet<>();
            for (SchemaSearchService.TableSearchResult tableResult : relevantTables) {
                allTableIds.add(tableResult.getTable().getTableId());
            }

            // 添加关联表ID
            if (searchResult.getRelatedTableIds() != null) {
                allTableIds.addAll(searchResult.getRelatedTableIds());
            }

            // 3. 构建精简Schema
            StringBuilder sb = new StringBuilder();
            sb.append("# 相关数据库 Schema 信息\n\n");
            sb.append(String.format("根据查询'%s'筛选出 %d 张最相关的表：\n\n", question, allTableIds.size()));

            // 主要表（向量检索到的）
            sb.append("## 主要相关表\n");
            for (SchemaSearchService.TableSearchResult tableResult : relevantTables) {
                appendCompactTableInfo(sb, tableResult);
            }

            // 关联表（通过外键关系）
            List<Long> relatedIds = new ArrayList<>(allTableIds);
            relatedIds.removeAll(relevantTables.stream()
                .map(r -> r.getTable().getTableId())
                .collect(Collectors.toList()));

            if (!relatedIds.isEmpty()) {
                sb.append("\n## 关联表（通过外键关系）\n");
                for (Long tableId : relatedIds) {
                    Optional<TableNode> tableOpt = tableNodeRepository.findByTableId(tableId);
                    if (tableOpt.isPresent()) {
                        appendSimpleTableInfo(sb, tableOpt.get());
                    }
                }
            }

            log.info("精简 Schema 构建完成，共 {} 张表（主要 {} 张，关联 {} 张）",
                allTableIds.size(), relevantTables.size(), relatedIds.size());

            return sb.toString();

        } catch (Exception e) {
            log.error("构建精简 Schema 失败: {}", e.getMessage(), e);
            // 失败时回退到全量Schema
            log.info("回退到全量 Schema");
            return buildFullSchemaContext(dsId);
        }
    }

    @Override
    public String buildSchemaWithSelectedTables(List<Long> tableIds, Long dsId) {
        log.info("根据选定的表列表构建 Schema，数据源ID: {}", dsId);

        StringBuilder sb = new StringBuilder();
        sb.append("# 选定的数据库 Schema 信息\n\n");

        for (Long tableId : tableIds) {
            Optional<TableNode> tableOpt = tableNodeRepository.findById(tableId);
            if (tableOpt.isPresent()) {
                appendTableInfo(sb, tableOpt.get(), true);
            }
        }

        return sb.toString();
    }

    // ==================== 私有辅助方法 ====================

    private void appendTableInfo(StringBuilder sb, TableNode table, boolean includeAllFields) {
        sb.append("### ").append(table.getTableName());
        if (table.getTableComment() != null && !table.getTableComment().isEmpty()) {
            sb.append(" (").append(table.getTableComment()).append(")");
        }
        sb.append("\n");

        List<FieldNode> fields = fieldNodeRepository.findByTableId(table.getTableId());
        if (fields == null || fields.isEmpty()) {
            sb.append("*无字段信息*\n\n");
            return;
        }

        // 限制字段数量
        int fieldCount = includeAllFields ? fields.size() : Math.min(fields.size(), maxFieldsPerTable);

        sb.append("**字段列表**（共 ").append(fields.size()).append(" 个）:\n");
        for (int i = 0; i < fieldCount; i++) {
            FieldNode field = fields.get(i);
            sb.append("- ").append(field.getFieldName())
              .append(" ").append(field.getFieldType());
            if (Boolean.TRUE.equals(field.getIsPrimary())) {
                sb.append(" [主键]");
            }
            if (field.getFieldComment() != null && !field.getFieldComment().isEmpty()) {
                sb.append(" -- ").append(field.getFieldComment());
            }
            sb.append("\n");
        }
        if (fields.size() > fieldCount) {
            sb.append("- ... 还有 ").append(fields.size() - fieldCount).append(" 个字段\n");
        }
        sb.append("\n");
    }

    private void appendCompactTableInfo(StringBuilder sb, SchemaSearchService.TableSearchResult tableResult) {
        TableNode table = tableResult.getTable();
        sb.append("### ").append(table.getTableName());
        if (table.getTableComment() != null && !table.getTableComment().isEmpty()) {
            sb.append(" (").append(table.getTableComment()).append(")");
        }
        sb.append(" [相关度: ").append(String.format("%.2f", tableResult.getScore())).append("]\n");

        // 显示相关字段（如果有）
        List<SchemaSearchService.FieldSearchResult> fields = tableResult.getRelevantFields();
        if (fields != null && !fields.isEmpty()) {
            sb.append("**相关字段**:\n");
            for (SchemaSearchService.FieldSearchResult fieldResult : fields) {
                FieldNode field = fieldResult.getField();
                sb.append("- ").append(field.getFieldName())
                  .append(" ").append(field.getFieldType());
                if (field.getFieldComment() != null && !field.getFieldComment().isEmpty()) {
                    sb.append(" (").append(field.getFieldComment()).append(")");
                }
                sb.append(" [相关度: ").append(String.format("%.2f", fieldResult.getScore())).append("]\n");
            }
        }

        sb.append("\n");
    }

    private void appendSimpleTableInfo(StringBuilder sb, TableNode table) {
        sb.append("- **").append(table.getTableName()).append("**");
        if (table.getTableComment() != null && !table.getTableComment().isEmpty()) {
            sb.append(": ").append(table.getTableComment());
        }
        sb.append("\n");
    }

    private void appendTableRelations(StringBuilder sb, Long dsId) {
        try {
            // 使用 Neo4jClient 查询表关系
            String query = "MATCH (t1:Table {dsId: $dsId})-[r:AI_RELATION]->(t2:Table) " +
                    "RETURN t1.tableName as sourceTableName, t2.tableName as targetTableName, " +
                    "r.relationType as relationType, r.sourceFields as sourceFields, " +
                    "r.targetFields as targetFields";

            Collection<Map<String, Object>> relations = neo4jClient
                    .query(query)
                    .bind(dsId).to("dsId")
                    .fetch().all();

            if (relations != null && !relations.isEmpty()) {
                sb.append("## 表之间的关系\n");
                for (Map<String, Object> rel : relations) {
                    String sourceTable = (String) rel.get("sourceTableName");
                    String targetTable = (String) rel.get("targetTableName");
                    String relationType = (String) rel.get("relationType");
                    String sourceFields = (String) rel.get("sourceFields");
                    String targetFields = (String) rel.get("targetFields");

                    sb.append("- ").append(sourceTable).append(" -> ").append(targetTable)
                            .append(" [").append(relationType).append("]");
                    if (sourceFields != null && !sourceFields.isEmpty()
                            && targetFields != null && !targetFields.isEmpty()) {
                        sb.append(" (").append(sourceTable).append(".").append(sourceFields)
                                .append(" = ").append(targetTable).append(".").append(targetFields).append(")");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            log.warn("查询表关系失败: {}", e.getMessage());
        }
    }
}
