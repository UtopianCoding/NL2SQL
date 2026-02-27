package com.nl2sql.model.dto;

import lombok.Data;

@Data
public class FieldDetailDTO {

    private Long fieldId;
    private String fieldName;
    private String fieldType;
    private String fieldComment;
    private Boolean isPrimary;
}
