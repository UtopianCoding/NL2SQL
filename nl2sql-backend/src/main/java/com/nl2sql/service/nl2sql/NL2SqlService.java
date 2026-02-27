package com.nl2sql.service.nl2sql;

import com.nl2sql.model.dto.QueryRequest;
import com.nl2sql.model.dto.QueryResponse;

public interface NL2SqlService {

    QueryResponse query(QueryRequest request, Long userId);
}
