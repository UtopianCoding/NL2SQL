package com.nl2sql.service.graph.impl;

import com.nl2sql.model.entity.FieldMeta;
import com.nl2sql.model.entity.TableMeta;
import com.nl2sql.model.graph.FieldNode;
import com.nl2sql.model.graph.TableNode;
import com.nl2sql.repository.neo4j.FieldNodeRepository;
import com.nl2sql.repository.neo4j.TableNodeRepository;
import com.nl2sql.service.graph.Neo4jService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class Neo4jServiceImpl implements Neo4jService {

    @Autowired
    private TableNodeRepository tableNodeRepository;

    @Autowired
    private FieldNodeRepository fieldNodeRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    @Override
    public void syncTableToGraph(TableMeta tableMeta, List<FieldMeta> fields) {
        TableNode tableNode = tableNodeRepository.findByTableId(tableMeta.getId())
                .orElse(new TableNode());
        tableNode.setTableId(tableMeta.getId());
        tableNode.setTableName(tableMeta.getTableName());
        tableNode.setTableComment(tableMeta.getTableComment());
        tableNode.setBusinessMeaning(tableMeta.getCustomComment());
        tableNode.setDsId(tableMeta.getDsId());
        tableNode.setDataVolume(tableMeta.getRowCount());
        tableNodeRepository.save(tableNode);

        for (FieldMeta field : fields) {
            FieldNode fieldNode = fieldNodeRepository.findByFieldId(field.getId())
                    .orElse(new FieldNode());
            fieldNode.setFieldId(field.getId());
            fieldNode.setFieldName(field.getFieldName());
            fieldNode.setFieldType(field.getFieldType());
            fieldNode.setFieldComment(field.getFieldComment());
            fieldNode.setBusinessMeaning(field.getCustomComment());
            fieldNode.setIsPrimary(field.getIsPrimary() == 1);
            fieldNode.setTableId(tableMeta.getId());
            fieldNodeRepository.save(fieldNode);

            fieldNodeRepository.createTableFieldRelation(tableMeta.getId(), field.getId());
        }

        log.info("同步表 {} 到Neo4j图谱完成，包含 {} 个字段", tableMeta.getTableName(), fields.size());
    }

    @Override
    public void createForeignKeyRelation(Long sourceTableId, Long targetTableId,
            String sourceField, String targetField) {
        tableNodeRepository.createForeignKeyRelation(sourceTableId, targetTableId, sourceField, targetField);
    }

    @Override
    public void updateJoinRelation(Long sourceTableId, Long targetTableId, String joinCondition) {
        tableNodeRepository.updateCommonJoinRelation(sourceTableId, targetTableId, joinCondition);
    }

    @Override
    public List<TableNode> getRelatedTables(Long tableId) {
        return tableNodeRepository.findRelatedTables(tableId);
    }

    @Override
    public List<TableNode> getTablesByDsId(Long dsId) {
        return tableNodeRepository.findByDsId(dsId);
    }

    @Override
    public void deleteByDsId(Long dsId) {
        tableNodeRepository.deleteAllByDsId(dsId);
    }

    @Override
    public void deleteTableNode(Long tableId) {
        tableNodeRepository.deleteByTableId(tableId);
    }

    @Override
    public String buildSchemaContext(Long dsId) {
        List<TableNode> tables = tableNodeRepository.findAllTablesByDsId(dsId);
        StringBuilder sb = new StringBuilder();

        for (TableNode table : tables) {
            sb.append("表名: ").append(table.getTableName());
            if (table.getTableComment() != null) {
                sb.append(" (").append(table.getTableComment()).append(")");
            }
            sb.append("\n字段:\n");

            List<FieldNode> fields = fieldNodeRepository.findByTableId(table.getTableId());
            for (FieldNode field : fields) {
                sb.append("  - ").append(field.getFieldName())
                  .append(" ").append(field.getFieldType());
                if (field.getIsPrimary()) {
                    sb.append(" [主键]");
                }
                if (field.getFieldComment() != null) {
                    sb.append(" -- ").append(field.getFieldComment());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 添加表关系信息
        Collection<Map<String, Object>> relations = neo4jClient
                .query("MATCH (t1:Table {dsId: $dsId})-[r:AI_RELATION]->(t2:Table) " +
                       "RETURN t1.tableName as sourceTableName, t2.tableName as targetTableName, " +
                       "r.relationType as relationType, r.sourceFields as sourceFields, " +
                       "r.targetFields as targetFields")
                .bind(dsId).to("dsId")
                .fetch().all();
        if (!relations.isEmpty()) {
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

        return sb.toString();
    }
}
