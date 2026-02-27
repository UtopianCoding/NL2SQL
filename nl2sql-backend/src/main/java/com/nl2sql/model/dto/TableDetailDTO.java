package com.nl2sql.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class TableDetailDTO {

    private Long tableId;
    private String tableName;
    private String tableComment;
    private List<FieldDetailDTO> fields;
}
