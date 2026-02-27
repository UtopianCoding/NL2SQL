package com.nl2sql.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class ERDiagramDTO {

    private List<TableDetailDTO> tables;
    private List<TableRelationDTO> relations;
    private Boolean analyzed;
}
