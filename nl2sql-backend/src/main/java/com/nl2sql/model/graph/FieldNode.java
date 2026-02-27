package com.nl2sql.model.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Data
@Node("Field")
public class FieldNode {

    @Id
    private Long fieldId;

    @Property("fieldName")
    private String fieldName;

    @Property("fieldType")
    private String fieldType;

    @Property("fieldComment")
    private String fieldComment;

    @Property("businessMeaning")
    private String businessMeaning;

    @Property("isPrimary")
    private Boolean isPrimary = false;

    @Property("isForeign")
    private Boolean isForeign = false;

    @Property("tableId")
    private Long tableId;
}
