package com.nl2sql.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("table_relation")
public class TableRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dsId;

    private Long sourceTableId;

    private String sourceTableName;

    private Long targetTableId;

    private String targetTableName;

    private String relationType;

    private String sourceFields;

    private String targetFields;

    private Double confidence;

    private String reasoning;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
