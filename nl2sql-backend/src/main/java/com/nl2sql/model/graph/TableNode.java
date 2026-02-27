package com.nl2sql.model.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Data
@Node("Table")
public class TableNode {

    @Id
    private Long tableId;

    @Property("tableName")
    private String tableName;

    @Property("tableComment")
    private String tableComment;

    @Property("businessMeaning")
    private String businessMeaning;

    @Property("accessFrequency")
    private Integer accessFrequency = 0;

    @Property("dataVolume")
    private Long dataVolume;

    @Property("dsId")
    private Long dsId;

    @Relationship(type = "HAS_FIELD", direction = Relationship.Direction.OUTGOING)
    private Set<FieldNode> fields = new HashSet<>();

    @Relationship(type = "FOREIGN_KEY", direction = Relationship.Direction.OUTGOING)
    private Set<TableNode> foreignKeyTables = new HashSet<>();
}
