package com.nl2sql.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("table_meta")
public class TableMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dsId;

    private String tableName;

    private String tableComment;

    private String customComment;

    private String tableType;

    private Long rowCount;

    private Integer syncStatus;

    private LocalDateTime syncTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
