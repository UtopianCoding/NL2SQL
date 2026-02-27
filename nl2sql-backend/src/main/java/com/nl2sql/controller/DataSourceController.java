package com.nl2sql.controller;

import com.nl2sql.common.Result;
import com.nl2sql.model.dto.DataSourceDTO;
import com.nl2sql.model.entity.DataSource;
import com.nl2sql.model.entity.SyncTask;
import com.nl2sql.service.datasource.DataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasource")
@Tag(name = "数据源管理", description = "数据源的增删改查、连接测试、元数据同步")
public class DataSourceController {

    @Autowired
    private DataSourceService dataSourceService;

    @GetMapping("/list")
    @Operation(summary = "获取数据源列表")
    public Result<List<DataSource>> list(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<DataSource> list = dataSourceService.listByUser(userId);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取数据源详情")
    public Result<DataSource> getById(@PathVariable Long id) {
        DataSource ds = dataSourceService.getById(id);
        return Result.success(ds);
    }

    @PostMapping("/add")
    @Operation(summary = "新增数据源")
    public Result<DataSource> add(@Valid @RequestBody DataSourceDTO dto, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        DataSource ds = dataSourceService.create(dto, userId);
        return Result.success(ds);
    }

    @PutMapping("/update/{id}")
    @Operation(summary = "更新数据源")
    public Result<DataSource> update(@PathVariable Long id, @Valid @RequestBody DataSourceDTO dto) {
        DataSource ds = dataSourceService.update(id, dto);
        return Result.success(ds);
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除数据源")
    public Result<Void> delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return Result.success();
    }

    @PostMapping("/test")
    @Operation(summary = "测试数据源连接")
    public Result<Boolean> testConnection(@Valid @RequestBody DataSourceDTO dto) {
        boolean success = dataSourceService.testConnection(dto);
        return Result.success(success);
    }

    @GetMapping("/{id}/tables")
    @Operation(summary = "获取数据源中的表列表")
    public Result<List<Map<String, Object>>> getTables(@PathVariable Long id) {
        List<Map<String, Object>> tables = dataSourceService.getTables(id);
        return Result.success(tables);
    }

    @GetMapping("/{id}/synced-tables")
    @Operation(summary = "获取已同步的表名列表")
    public Result<List<String>> getSyncedTableNames(@PathVariable Long id) {
        List<String> tableNames = dataSourceService.getSyncedTableNames(id);
        return Result.success(tableNames);
    }

    @PostMapping("/{id}/sync")
    @Operation(summary = "异步同步表元数据")
    public Result<Long> syncTables(@PathVariable Long id, @RequestBody List<String> tableNames) {
        Long taskId = dataSourceService.syncTablesAsync(id, tableNames);
        return Result.success(taskId);
    }

    @GetMapping("/sync/progress/{taskId}")
    @Operation(summary = "获取同步任务进度")
    public Result<SyncTask> getSyncProgress(@PathVariable Long taskId) {
        SyncTask task = dataSourceService.getSyncTaskProgress(taskId);
        return Result.success(task);
    }
}
