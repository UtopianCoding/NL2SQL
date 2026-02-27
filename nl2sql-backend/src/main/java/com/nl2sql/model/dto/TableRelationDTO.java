package com.nl2sql.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class TableRelationDTO {

    private Long relationId;
    private Long sourceTableId;
    private String sourceTableName;
    private Long targetTableId;
    private String targetTableName;
    private String relationType;
    private Double confidence;
    private String reasoning;
    private List<String> sourceFields;
    private List<String> targetFields;
}
