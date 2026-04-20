package com.nl2sql.service.nl2sql.impl;

import com.nl2sql.model.graph.FieldNode;
import com.nl2sql.model.graph.TableNode;
import com.nl2sql.repository.neo4j.FieldNodeRepository;
import com.nl2sql.repository.neo4j.TableNodeRepository;
import com.nl2sql.service.nl2sql.SchemaContextService;
import com.nl2sql.service.vector.SchemaSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Schema 上下文服务实现
 * 提供全量Schema和精简 Schema 的功能
 */
@Slf4j
@Service
public class SchemaContextServiceImpl implements SchemaContextService {

    @Autowired
    private TableNodeRepository tableNodeRepository;

    @Autowired
    private FieldNodeRepository fieldNodeRepository;

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

    /** 向量不可用时的回退：最多带几张表（避免大库拖垮 LLM 上下文） */
    @Value("${nl2sql.schema.fallback-max-tables:12}")
    private int fallbackMaxTables;

    /** 向量不可用时的回退：每表最多字段数 */
    @Value("${nl2sql.schema.fallback-max-fields-per-table:12}")
    private int fallbackMaxFieldsPerTable;

    /** 精简模式下，外键扩展的关联表最多追加几张 */
    @Value("${nl2sql.schema.max-related-tables-compact:8}")
    private int maxRelatedTablesCompact;

    @Override
    @Cacheable(cacheNames = "schema-context-full", key = "#dsId")
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

            int tableCount = Math.min(tables.size(), maxTables);
            sb.append(String.format("共 %d 张表（显示前 %d 张，每表最多 %d 个字段）：\n\n",
                    tables.size(), tableCount, maxFieldsPerTable));

            Set<Long> includedTableIds = new HashSet<>();
            for (int i = 0; i < tableCount; i++) {
                TableNode table = tables.get(i);
                includedTableIds.add(table.getTableId());
                appendTableInfo(sb, table, maxFieldsPerTable);
            }

            appendTableRelations(sb, dsId, includedTableIds);

            log.info("全量 Schema 构建完成，共 {} 张表", tableCount);
            return sb.toString();

        } catch (Exception e) {
            log.error("构建全量 Schema 失败: {}", e.getMessage(), e);
            return "";
        }
    }

    @Override
    @Cacheable(cacheNames = "schema-context-compact", key = "#dsId + '::' + #question")
    public String buildCompactSchemaContext(String question, Long dsId) {
        log.info("构建精简 Schema 上下文，查询: '{}', 数据源ID: {}", question, dsId);

        try {
            SchemaSearchService.TableFieldSearchResult searchResult =
                    schemaSearchService.searchRelevantTablesAndFields(
                            question, dsId, defaultTopK, defaultFieldTopK);

            List<SchemaSearchService.TableSearchResult> relevantTables = searchResult.getTables();

            if (relevantTables == null || relevantTables.isEmpty()) {
                log.warn("向量检索未找到相关表，回退到截断 Schema（非全量）");
                return buildFallbackSchemaContext(dsId);
            }

            Set<Long> mainTableIds = new LinkedHashSet<>();
            for (SchemaSearchService.TableSearchResult tableResult : relevantTables) {
                mainTableIds.add(tableResult.getTable().getTableId());
            }

            Set<Long> extraRelated = new LinkedHashSet<>();
            if (searchResult.getRelatedTableIds() != null) {
                for (Long id : searchResult.getRelatedTableIds()) {
                    if (id != null && !mainTableIds.contains(id)) {
                        extraRelated.add(id);
                    }
                }
            }

            List<Long> relatedSorted = new ArrayList<>(extraRelated);
            relatedSorted.sort(Comparator.naturalOrder());
            if (relatedSorted.size() > maxRelatedTablesCompact) {
                log.info("关联表数量 {} 超过上限 {}，已截断", relatedSorted.size(), maxRelatedTablesCompact);
                relatedSorted = relatedSorted.subList(0, maxRelatedTablesCompact);
            }

            Set<Long> tablesInPrompt = new LinkedHashSet<>(mainTableIds);
            tablesInPrompt.addAll(relatedSorted);

            StringBuilder sb = new StringBuilder();
            sb.append("# 相关数据库 Schema 信息\n\n");
            sb.append(String.format("根据查询「%s」检索：主要相关表 %d 张", question, mainTableIds.size()));
            if (!relatedSorted.isEmpty()) {
                sb.append(String.format("，外键扩展关联表 %d 张（最多 %d 张）",
                        relatedSorted.size(), maxRelatedTablesCompact));
            }
            sb.append("。\n\n");

            sb.append("## 主要相关表\n");
            for (SchemaSearchService.TableSearchResult tableResult : relevantTables) {
                appendCompactTableInfo(sb, tableResult);
            }

            if (!relatedSorted.isEmpty()) {
                sb.append("\n## 关联表（通过外键关系）\n");
                for (Long tableId : relatedSorted) {
                    Optional<TableNode> tableOpt = tableNodeRepository.findByTableId(tableId);
                    tableOpt.ifPresent(table -> appendSimpleTableInfo(sb, table));
                }
            }

            appendTableRelations(sb, dsId, tablesInPrompt);

            log.info("精简 Schema 构建完成");
            return sb.toString();

        } catch (Exception e) {
            log.error("构建精简 Schema 失败: {}", e.getMessage(), e);
            log.info("回退到截断 Schema");
            return buildFallbackSchemaContext(dsId);
        }
    }

    @Override
    @Cacheable(cacheNames = "schema-context-selected", key = "#dsId + '::' + T(java.lang.String).join(',', #tableIds)")
    public String buildSchemaWithSelectedTables(List<Long> tableIds, Long dsId) {
        log.info("根据选定的表列表构建 Schema，数据源ID: {}", dsId);

        StringBuilder sb = new StringBuilder();
        sb.append("# 选定的数据库 Schema 信息\n\n");

        Set<Long> included = new HashSet<>();
        for (Long tableId : tableIds) {
            Optional<TableNode> tableOpt = tableNodeRepository.findById(tableId);
            if (tableOpt.isPresent()) {
                TableNode t = tableOpt.get();
                included.add(t.getTableId());
                appendTableInfo(sb, t, maxFieldsPerTable);
            }
        }

        appendTableRelations(sb, dsId, included);

        return sb.toString();
    }

    /**
     * 向量检索不可用时：用更小的表数/字段上限构建上下文，避免把大模型上下文撑满。
     */
    private String buildFallbackSchemaContext(Long dsId) {
        try {
            List<TableNode> tables = tableNodeRepository.findAllTablesByDsId(dsId);
            if (tables == null || tables.isEmpty()) {
                log.warn("数据源 {} 没有找到任何表（回退）", dsId);
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# 数据库 Schema 信息（向量检索不可用 · 已截断）\n\n");

            int tableCount = Math.min(tables.size(), fallbackMaxTables);
            sb.append(String.format("共 %d 张表（为控制上下文长度，仅展示前 %d 张，每表最多 %d 个字段）。\n\n",
                    tables.size(), tableCount, fallbackMaxFieldsPerTable));

            Set<Long> includedTableIds = new HashSet<>();
            for (int i = 0; i < tableCount; i++) {
                TableNode table = tables.get(i);
                includedTableIds.add(table.getTableId());
                appendTableInfo(sb, table, fallbackMaxFieldsPerTable);
            }

            appendTableRelations(sb, dsId, includedTableIds);

            return sb.toString();
        } catch (Exception e) {
            log.error("构建截断回退 Schema 失败: {}", e.getMessage(), e);
            return "";
        }
    }

    // ==================== 私有辅助方法 ====================

    private void appendTableInfo(StringBuilder sb, TableNode table, int maxFieldsCap) {
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

        int fieldCount = Math.min(fields.size(), maxFieldsCap);

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
            sb.append("- ... 还有 ").append(fields.size() - fieldCount).append(" 个字段未列出\n");
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

    /**
     * 只输出「已选表集合内部」的 AI 关系边，避免表列表截断后仍打印全库关系导致上下文爆炸。
     */
    private void appendTableRelations(StringBuilder sb, Long dsId, Set<Long> includedTableIds) {
        if (includedTableIds == null || includedTableIds.isEmpty()) {
            return;
        }
        try {
            List<Map<String, Object>> relations = tableNodeRepository.findAIRelationsByDsId(dsId);
            if (relations == null || relations.isEmpty()) {
                return;
            }

            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> rel : relations) {
                Long s = (Long) rel.get("sourceTableId");
                Long t = (Long) rel.get("targetTableId");
                if (s != null && t != null
                        && includedTableIds.contains(s)
                        && includedTableIds.contains(t)) {
                    filtered.add(rel);
                }
            }

            if (filtered.isEmpty()) {
                return;
            }

            sb.append("## 表之间的关系（限于上文已出现的表）\n");
            for (Map<String, Object> rel : filtered) {
                String sourceTable = (String) rel.get("sourceTableName");
                String targetTable = (String) rel.get("targetTableName");
                String relationType = (String) rel.get("relationType");
                String sourceFields = (String) rel.get("sourceFields");
                String targetFields = (String) rel.get("targetFields");

                sb.append("- ").append(sourceTable).append(" -> ").append(targetTable)
                        .append(" [").append(relationType != null ? relationType : "").append("]");
                if (sourceFields != null && !sourceFields.isEmpty()
                        && targetFields != null && !targetFields.isEmpty()) {
                    sb.append(" (").append(sourceTable).append(".").append(sourceFields)
                            .append(" = ").append(targetTable).append(".").append(targetFields).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        } catch (Exception e) {
            log.warn("查询表关系失败: {}", e.getMessage());
        }
    }
}
