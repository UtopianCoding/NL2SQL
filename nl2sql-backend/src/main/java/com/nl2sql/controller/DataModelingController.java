package com.nl2sql.controller;

import com.nl2sql.common.Result;
import com.nl2sql.model.dto.ERDiagramDTO;
import com.nl2sql.model.entity.SyncTask;
import com.nl2sql.service.datamodeling.DataModelingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/datamodeling")
@Tag(name = "数据建模", description = "ER图展示、AI关系分析")
public class DataModelingController {

    @Autowired
    private DataModelingService dataModelingService;

    @GetMapping("/{dsId}/er-diagram")
    @Operation(summary = "获取ER图数据")
    public Result<ERDiagramDTO> getERDiagram(@PathVariable Long dsId) {
        ERDiagramDTO data = dataModelingService.getERDiagram(dsId);
        return Result.success(data);
    }

    @PostMapping("/{dsId}/analyze")
    @Operation(summary = "触发AI分析表关系")
    public Result<Long> analyze(@PathVariable Long dsId) {
        Long taskId = dataModelingService.analyzeRelationsAsync(dsId);
        return Result.success(taskId);
    }

    @GetMapping("/analyze-progress/{taskId}")
    @Operation(summary = "查询AI分析进度")
    public Result<SyncTask> getAnalysisProgress(@PathVariable Long taskId) {
        SyncTask task = dataModelingService.getAnalysisProgress(taskId);
        return Result.success(task);
    }

    @DeleteMapping("/relation/{relationId}")
    @Operation(summary = "删除关系")
    public Result<Void> deleteRelation(@PathVariable Long relationId) {
        dataModelingService.deleteRelation(relationId);
        return Result.success();
    }

    @PutMapping("/field/{fieldId}/comment")
    @Operation(summary = "更新字段注释")
    public Result<Void> updateFieldComment(@PathVariable Long fieldId, @RequestBody Map<String, String> body) {
        String comment = body.get("comment");
        dataModelingService.updateFieldComment(fieldId, comment);
        return Result.success();
    }

    @PutMapping("/relation/{relationId}")
    @Operation(summary = "更新关系类型")
    public Result<Void> updateRelation(@PathVariable Long relationId, @RequestBody Map<String, String> body) {
        String relationType = body.get("relationType");
        dataModelingService.updateRelation(relationId, relationType);
        return Result.success();
    }

    @PostMapping("/{dsId}/relation")
    @Operation(summary = "手动新增关系")
    public Result<Void> createRelation(@PathVariable Long dsId, @RequestBody Map<String, Object> body) {
        Long sourceTableId = Long.parseLong(body.get("sourceTableId").toString());
        Long targetTableId = Long.parseLong(body.get("targetTableId").toString());
        String relationType = (String) body.get("relationType");
        String sourceFields = (String) body.getOrDefault("sourceFields", "");
        String targetFields = (String) body.getOrDefault("targetFields", "");
        dataModelingService.createRelation(dsId, sourceTableId, targetTableId,
                relationType, sourceFields, targetFields);
        return Result.success();
    }
}
