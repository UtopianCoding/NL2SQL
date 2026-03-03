package com.nl2sql.service.datasource.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nl2sql.mapper.DataSourceMapper;
import com.nl2sql.mapper.FieldMetaMapper;
import com.nl2sql.mapper.SyncTaskMapper;
import com.nl2sql.mapper.TableMetaMapper;
import com.nl2sql.model.dto.DataSourceDTO;
import com.nl2sql.model.entity.DataSource;
import com.nl2sql.model.entity.FieldMeta;
import com.nl2sql.model.entity.SyncTask;
import com.nl2sql.model.entity.TableMeta;
import com.nl2sql.service.datasource.DataSourceService;
import com.nl2sql.service.datasource.SyncTaskService;
import com.nl2sql.service.graph.Neo4jService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DataSourceServiceImpl implements DataSourceService {

    @Autowired
    private DataSourceMapper dataSourceMapper;

    @Autowired
    private TableMetaMapper tableMetaMapper;

    @Autowired
    private FieldMetaMapper fieldMetaMapper;

    @Autowired
    private Neo4jService neo4jService;

    @Autowired
    private SyncTaskMapper syncTaskMapper;

    @Autowired
    private SyncTaskService syncTaskService;

    @Override
    public List<DataSource> listByUser(Long userId) {
        return dataSourceMapper.selectList(
                new LambdaQueryWrapper<DataSource>().eq(DataSource::getCreateBy, userId));
    }

    @Override
    public DataSource getById(Long id) {
        DataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new RuntimeException("数据源不存在");
        }
        return ds;
    }

    @Override
    @Transactional
    public DataSource create(DataSourceDTO dto, Long userId) {
        DataSource ds = new DataSource();
        ds.setName(dto.getName());
        ds.setType(dto.getType());
        ds.setHost(dto.getHost());
        ds.setPort(dto.getPort());
        ds.setDatabaseName(dto.getDatabaseName());
        ds.setUsername(dto.getUsername());
        ds.setPassword(dto.getPassword());
        ds.setConfig(dto.getConfig());
        ds.setCreateBy(userId);
        ds.setStatus(1);

        dataSourceMapper.insert(ds);
        return ds;
    }

    @Override
    @Transactional
    public DataSource update(Long id, DataSourceDTO dto) {
        DataSource ds = getById(id);
        ds.setName(dto.getName());
        ds.setType(dto.getType());
        ds.setHost(dto.getHost());
        ds.setPort(dto.getPort());
        ds.setDatabaseName(dto.getDatabaseName());
        ds.setUsername(dto.getUsername());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            ds.setPassword(dto.getPassword());
        }
        ds.setConfig(dto.getConfig());

        dataSourceMapper.updateById(ds);
        return ds;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        neo4jService.deleteByDsId(id);
        dataSourceMapper.deleteById(id);
    }

    @Override
    public boolean testConnection(DataSourceDTO dto) {
        String jdbcUrl = buildJdbcUrl(dto.getType(), dto.getHost(), dto.getPort(), dto.getDatabaseName());
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dto.getUsername(), dto.getPassword())) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("测试连接失败: {}", e.getMessage());
            throw new RuntimeException("连接失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getTables(Long dsId) {
        DataSource ds = getById(dsId);
        String jdbcUrl = buildJdbcUrl(ds.getType(), ds.getHost(), ds.getPort(), ds.getDatabaseName());

        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, ds.getUsername(), ds.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getTables(ds.getDatabaseName(), null, "%", new String[]{"TABLE", "VIEW"});

            while (rs.next()) {
                Map<String, Object> table = new HashMap<>();
                table.put("tableName", rs.getString("TABLE_NAME"));
                table.put("tableType", rs.getString("TABLE_TYPE"));
                table.put("remarks", rs.getString("REMARKS"));
                tables.add(table);
            }
        } catch (SQLException e) {
            log.error("获取表列表失败: {}", e.getMessage());
            throw new RuntimeException("获取表列表失败: " + e.getMessage());
        }

        return tables;
    }

    @Override
    public List<String> getSyncedTableNames(Long dsId) {
        List<TableMeta> tables = tableMetaMapper.selectList(
                new LambdaQueryWrapper<TableMeta>()
                        .eq(TableMeta::getDsId, dsId)
                        .eq(TableMeta::getSyncStatus, 1)
                        .orderByAsc(TableMeta::getTableName));
        return tables.stream().map(TableMeta::getTableName).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Long syncTablesAsync(Long dsId, List<String> tableNames) {
        // 清理不在新选择中的已同步表
        cleanRemovedTables(dsId, tableNames);

        SyncTask task = new SyncTask();
        task.setDsId(dsId);
        task.setTaskType("SYNC_TABLES");
        task.setStatus(SyncTask.STATUS_PENDING);
        task.setTotalCount(tableNames.size());
        task.setCurrentCount(0);
        syncTaskMapper.insert(task);

        // 调用独立的异步服务执行同步
        syncTaskService.executeSyncAsync(task.getId(), dsId, tableNames);
        return task.getId();
    }

    @Override
    public SyncTask getSyncTaskProgress(Long taskId) {
        return syncTaskMapper.selectById(taskId);
    }

    /**
     * 清理不在新选择列表中的已同步表及其字段和图数据
     */
    private void cleanRemovedTables(Long dsId, List<String> selectedTableNames) {
        List<TableMeta> existingTables = tableMetaMapper.selectList(
                new LambdaQueryWrapper<TableMeta>()
                        .eq(TableMeta::getDsId, dsId)
                        .eq(TableMeta::getSyncStatus, 1));

        java.util.Set<String> selectedSet = new java.util.HashSet<>(selectedTableNames);

        for (TableMeta table : existingTables) {
            if (!selectedSet.contains(table.getTableName())) {
                log.info("清理已取消选择的表: dsId={}, tableName={}", dsId, table.getTableName());
                // 删除字段元数据
                fieldMetaMapper.delete(new LambdaQueryWrapper<FieldMeta>()
                        .eq(FieldMeta::getTableId, table.getId()));
                // 删除Neo4j中的表节点
                neo4jService.deleteTableNode(table.getId());
                // 删除表元数据
                tableMetaMapper.deleteById(table.getId());
            }
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
            case "oceanbase" -> String.format("jdbc:mysql://%s:%d/%s", host, port, database);
            case "dm" -> String.format("jdbc:dm://%s:%d/%s", host, port, database);
            default -> throw new RuntimeException("不支持的数据库类型: " + type);
        };
    }
}
