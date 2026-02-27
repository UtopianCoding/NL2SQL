package com.nl2sql.service.llm;

import com.nl2sql.model.dto.QueryResponse;

public interface LLMProvider {
    
    String getName();
    
    String generateSql(String prompt);
    
    float[] generateEmbedding(String text);
    
    default QueryResponse.ColumnInfo[] parseColumns(String sql) {
        return new QueryResponse.ColumnInfo[0];
    }
}
