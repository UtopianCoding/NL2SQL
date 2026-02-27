package com.nl2sql.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("query_history")
public class QueryHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long dsId;

    private Long conversationId;

    private String naturalQuery;

    private String generatedSql;

    private String executionStatus;

    private Integer resultRows;

    private Integer executionTimeMs;

    private String errorMessage;

    private Integer isFavorite;

    private Integer isFromCache;

    private String llmModel;

    private Integer llmTokens;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
