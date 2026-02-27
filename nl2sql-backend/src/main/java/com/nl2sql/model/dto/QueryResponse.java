package com.nl2sql.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {

    private Long historyId;
    private Long conversationId;
    private String question;
    private String sql;
    private String explanation;
    private List<Map<String, Object>> data;
    private List<ColumnInfo> columns;
    private Integer totalRows;
    private Long executionTimeMs;
    private Boolean fromCache;
    private String status;
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnInfo {
        private String name;
        private String type;
        private String comment;
    }
}
