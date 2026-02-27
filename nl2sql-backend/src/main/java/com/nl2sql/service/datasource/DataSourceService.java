package com.nl2sql.service.datasource;

import com.nl2sql.model.dto.DataSourceDTO;
import com.nl2sql.model.entity.DataSource;
import com.nl2sql.model.entity.SyncTask;

import java.util.List;
import java.util.Map;

public interface DataSourceService {

    List<DataSource> listByUser(Long userId);

    DataSource getById(Long id);

    DataSource create(DataSourceDTO dto, Long userId);

    DataSource update(Long id, DataSourceDTO dto);

    void delete(Long id);

    boolean testConnection(DataSourceDTO dto);

    List<Map<String, Object>> getTables(Long dsId);

    List<String> getSyncedTableNames(Long dsId);

    Long syncTablesAsync(Long dsId, List<String> tableNames);

    SyncTask getSyncTaskProgress(Long taskId);
}
