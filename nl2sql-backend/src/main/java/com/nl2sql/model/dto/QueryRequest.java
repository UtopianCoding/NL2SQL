package com.nl2sql.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QueryRequest {

    @NotNull(message = "数据源ID不能为空")
    private Long dsId;

    @NotBlank(message = "查询问题不能为空")
    private String question;

    private Long conversationId;

    private Boolean useCache = true;
}
