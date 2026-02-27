package com.nl2sql.service.graph;

import com.nl2sql.model.entity.FieldMeta;
import com.nl2sql.model.entity.TableMeta;
import com.nl2sql.model.graph.TableNode;

import java.util.List;

public interface Neo4jService {

    void syncTableToGraph(TableMeta tableMeta, List<FieldMeta> fields);

    void createForeignKeyRelation(Long sourceTableId, Long targetTableId,
            String sourceField, String targetField);

    void updateJoinRelation(Long sourceTableId, Long targetTableId, String joinCondition);

    List<TableNode> getRelatedTables(Long tableId);

    List<TableNode> getTablesByDsId(Long dsId);

    void deleteByDsId(Long dsId);

    void deleteTableNode(Long tableId);

    String buildSchemaContext(Long dsId);
}
