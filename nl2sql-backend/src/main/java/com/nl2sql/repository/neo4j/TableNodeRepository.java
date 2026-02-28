package com.nl2sql.repository.neo4j;

import com.nl2sql.model.graph.TableNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface TableNodeRepository extends Neo4jRepository<TableNode, Long> {

    Optional<TableNode> findByTableId(Long tableId);

    List<TableNode> findByDsId(Long dsId);

    @Query("MATCH (t:Table {dsId: $dsId}) RETURN t")
    List<TableNode> findAllTablesByDsId(Long dsId);

    @Query("MATCH (t:Table {tableId: $tableId})-[:HAS_FIELD]->(f:Field) RETURN t, collect(f) as fields")
    Optional<TableNode> findTableWithFields(Long tableId);

    @Query("MATCH (t1:Table {tableId: $tableId})-[:FOREIGN_KEY]->(t2:Table) RETURN t2")
    List<TableNode> findRelatedTables(Long tableId);

    @Query("MATCH (t:Table {dsId: $dsId})-[:HAS_FIELD]->(f:Field) DETACH DELETE f, t")
    void deleteAllByDsId(Long dsId);

    @Query("MATCH (t:Table {tableId: $tableId}) OPTIONAL MATCH (t)-[:HAS_FIELD]->(f:Field) DETACH DELETE f, t")
    void deleteByTableId(Long tableId);

    @Query("""
        MATCH (t1:Table {tableId: $sourceTableId}), (t2:Table {tableId: $targetTableId})
        MERGE (t1)-[r:FOREIGN_KEY {sourceField: $sourceField, targetField: $targetField}]->(t2)
        RETURN r
        """)
    void createForeignKeyRelation(Long sourceTableId, Long targetTableId, String sourceField, String targetField);

    @Query("""
        MATCH (t1:Table {tableId: $sourceTableId}), (t2:Table {tableId: $targetTableId})
        MERGE (t1)-[r:COMMON_JOIN {joinCondition: $joinCondition}]->(t2)
        ON CREATE SET r.joinFrequency = 1
        ON MATCH SET r.joinFrequency = r.joinFrequency + 1
        RETURN r
        """)
    void updateCommonJoinRelation(Long sourceTableId, Long targetTableId, String joinCondition);

    @Query("""
        MATCH (t1:Table {tableId: $sourceTableId}), (t2:Table {tableId: $targetTableId})
        MERGE (t1)-[r:AI_RELATION]->(t2)
        SET r.relationType = $relationType, r.confidence = $confidence,
            r.reasoning = $reasoning, r.sourceFields = $sourceFields,
            r.targetFields = $targetFields, r.analyzedBy = $model
        RETURN r
        """)
    void createAIRelation(Long sourceTableId, Long targetTableId, String relationType,
                          Double confidence, String reasoning, String sourceFields,
                          String targetFields, String model);

    @Query("""
        MATCH (t1:Table {dsId: $dsId})-[r:AI_RELATION]->(t2:Table)
        RETURN t1.tableId as sourceTableId, t1.tableName as sourceTableName,
               t2.tableId as targetTableId, t2.tableName as targetTableName,
               r.relationType as relationType, r.confidence as confidence,
               r.reasoning as reasoning, r.sourceFields as sourceFields,
               r.targetFields as targetFields, elementId(r) as relationId
        """)
    List<Map<String, Object>> findAIRelationsByDsId(Long dsId);

    @Query("MATCH (t:Table {dsId: $dsId})-[r:AI_RELATION]-() DELETE r")
    void deleteAllAIRelationsByDsId(Long dsId);

    @Query("MATCH ()-[r:AI_RELATION]->() WHERE elementId(r) = $relationId DELETE r")
    void deleteAIRelation(String relationId);
}
