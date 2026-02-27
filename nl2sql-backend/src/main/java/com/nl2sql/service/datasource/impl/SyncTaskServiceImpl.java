package com.nl2sql.service.datasource.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nl2sql.mapper.DataSourceMapper;
import com.nl2sql.mapper.FieldMetaMapper;
import com.nl2sql.mapper.SyncTaskMapper;
import com.nl2sql.mapper.TableMetaMapper;
import com.nl2sql.model.entity.DataSource;
import com.nl2sql.model.entity.FieldMeta;
import com.nl2sql.model.entity.SyncTask;
import com.nl2sql.model.entity.TableMeta;
import com.nl2sql.service.datasource.SyncTaskService;
import com.nl2sql.service.graph.Neo4jService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class SyncTaskServiceImpl implements SyncTaskService {

    @Autowired
    private DataSourceMapper dataSourceMapper;

    @Autowired
    private TableMetaMapper tableMetaMapper;

    @Autowired
    private FieldMetaMapper fieldMetaMapper;

    @Autowired
    private SyncTaskMapper syncTaskMapper;

    @Autowired
    private Neo4jService neo4jService;

    @Override
    @Async
    public void executeSyncAsync(Long taskId, Long dsId, List<String> tableNames) {
        log.info("开始异步同步任务: taskId={}, dsId={}, tables={}", taskId, dsId, tableNames.size());
        
        SyncTask task = syncTaskMapper.selectById(taskId);
        task.setStatus(SyncTask.STATUS_RUNNING);
        syncTaskMapper.updateById(task);

        DataSource ds = dataSourceMapper.selectById(dsId);
        if (ds == null) {
            task.setStatus(SyncTask.STATUS_FAILED);
            task.setErrorMessage("数据源不存在");
            syncTaskMapper.updateById(task);
            return;
        }

        String jdbcUrl = buildJdbcUrl(ds.getType(), ds.getHost(), ds.getPort(), ds.getDatabaseName());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, ds.getUsername(), ds.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();

            int processedCount = 0;
            for (String tableName : tableNames) {
                task.setCurrentTable(tableName);
                syncTaskMapper.updateById(task);

                ResultSet tableRs = metaData.getTables(ds.getDatabaseName(), null, tableName, null);
                if (tableRs.next()) {
                    TableMeta tableMeta = tableMetaMapper.selectOne(
                            new LambdaQueryWrapper<TableMeta>()
                                    .eq(TableMeta::getDsId, dsId)
                                    .eq(TableMeta::getTableName, tableName));
                    if (tableMeta == null) {
                        tableMeta = new TableMeta();
                    }
                    tableMeta.setDsId(dsId);
                    tableMeta.setTableName(tableName);
                    tableMeta.setTableComment(tableRs.getString("REMARKS"));
                    tableMeta.setTableType(tableRs.getString("TABLE_TYPE"));
                    tableMeta.setSyncStatus(1);
                    tableMeta.setSyncTime(LocalDateTime.now());

                    if (tableMeta.getId() == null) {
                        tableMetaMapper.insert(tableMeta);
                    } else {
                        tableMetaMapper.updateById(tableMeta);
                    }

                    // 删除旧的字段数据
                    fieldMetaMapper.delete(new LambdaQueryWrapper<FieldMeta>()
                            .eq(FieldMeta::getTableId, tableMeta.getId()));

                    // 获取主键字段名集合
                    java.util.Set<String> primaryKeys = new java.util.HashSet<>();
                    ResultSet pkRs = metaData.getPrimaryKeys(ds.getDatabaseName(), null, tableName);
                    while (pkRs.next()) {
                        primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                    }
                    pkRs.close();

                    ResultSet columnRs = metaData.getColumns(ds.getDatabaseName(), null, tableName, "%");
                    int fieldIndex = 0;
                    while (columnRs.next()) {
                        FieldMeta fieldMeta = new FieldMeta();
                        fieldMeta.setTableId(tableMeta.getId());
                        String columnName = columnRs.getString("COLUMN_NAME");
                        fieldMeta.setFieldName(columnName);
                        fieldMeta.setFieldType(columnRs.getString("TYPE_NAME"));
                        fieldMeta.setFieldComment(columnRs.getString("REMARKS"));
                        fieldMeta.setIsNullable("YES".equals(columnRs.getString("IS_NULLABLE")) ? 1 : 0);
                        fieldMeta.setDefaultValue(columnRs.getString("COLUMN_DEF"));
                        fieldMeta.setFieldIndex(fieldIndex++);
                        fieldMeta.setIsPrimary(primaryKeys.contains(columnName) ? 1 : 0);
                        fieldMetaMapper.insert(fieldMeta);
                    }

                    List<FieldMeta> fields = fieldMetaMapper.selectList(
                            new LambdaQueryWrapper<FieldMeta>()
                                    .eq(FieldMeta::getTableId, tableMeta.getId())
                                    .orderByAsc(FieldMeta::getFieldIndex));
                    neo4jService.syncTableToGraph(tableMeta, fields);
                }

                processedCount++;
                task.setCurrentCount(processedCount);
                syncTaskMapper.updateById(task);
                
                log.info("同步进度: {}/{}", processedCount, tableNames.size());
            }

            ds.setStatus(1);
            dataSourceMapper.updateById(ds);

            task.setStatus(SyncTask.STATUS_SUCCESS);
            task.setCurrentTable(null);
            syncTaskMapper.updateById(task);
            
            log.info("同步任务完成: taskId={}", taskId);
        } catch (SQLException e) {
            log.error("同步表结构失败: {}", e.getMessage(), e);
            task.setStatus(SyncTask.STATUS_FAILED);
            task.setErrorMessage(e.getMessage());
            syncTaskMapper.updateById(task);
        }
    }

    private String buildJdbcUrl(String type, String host, int port, String database) {
        return switch (type.toLowerCase()) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true", host, port, database);
            case "postgresql", "pg" -> String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
            case "oracle" -> String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, database);
            case "sqlserver", "mssql" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false", host, port, database);
            case "gaussdb" -> String.format("jdbc:gaussdb://%s:%d/%s", host, port, database);
            case "kingbase" -> String.format("jdbc:kingbase8://%s:%d/%s", host, port, database);
            case "oceanbase" -> String.format("jdbc:oceanbase://%s:%d/%s", host, port, database);
            case "dm" -> String.format("jdbc:dm://%s:%d/%s", host, port, database);
            default -> throw new RuntimeException("不支持的数据库类型: " + type);
        };
    }
}
