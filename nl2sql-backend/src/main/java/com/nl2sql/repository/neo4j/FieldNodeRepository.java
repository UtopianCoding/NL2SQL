package com.nl2sql.repository.neo4j;

import com.nl2sql.model.graph.FieldNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldNodeRepository extends Neo4jRepository<FieldNode, Long> {

    Optional<FieldNode> findByFieldId(Long fieldId);

    List<FieldNode> findByTableId(Long tableId);

    @Query("MATCH (f:Field {tableId: $tableId}) DELETE f")
    void deleteAllByTableId(Long tableId);

    @Query("MATCH (t:Table {tableId: $tableId}), (f:Field {fieldId: $fieldId}) MERGE (t)-[:HAS_FIELD]->(f)")
    void createTableFieldRelation(Long tableId, Long fieldId);
}
