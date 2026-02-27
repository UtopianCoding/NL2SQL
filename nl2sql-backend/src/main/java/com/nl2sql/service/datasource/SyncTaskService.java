package com.nl2sql.service.datasource;

import java.util.List;

public interface SyncTaskService {
    void executeSyncAsync(Long taskId, Long dsId, List<String> tableNames);
}
