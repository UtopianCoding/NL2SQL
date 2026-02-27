package com.nl2sql.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("field_meta")
public class FieldMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tableId;

    private String fieldName;

    private String fieldType;

    private String fieldComment;

    private String customComment;

    private Integer isPrimary;

    private Integer isNullable;

    private String defaultValue;

    private Integer fieldIndex;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
