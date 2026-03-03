package com.nl2sql.controller;

import com.nl2sql.common.Result;
import com.nl2sql.model.entity.AiModelConfig;
import com.nl2sql.service.aimodel.AiModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-model")
@Tag(name = "AI模型配置", description = "AI模型的增删改查和默认模型设置")
public class AiModelConfigController {

    @Autowired
    private AiModelConfigService aiModelConfigService;

    @GetMapping("/list")
    @Operation(summary = "获取模型列表")
    public Result<List<AiModelConfig>> list(@RequestParam(required = false) String keyword) {
        List<AiModelConfig> list = aiModelConfigService.list(keyword);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取模型详情")
    public Result<AiModelConfig> getById(@PathVariable Long id) {
        AiModelConfig config = aiModelConfigService.getById(id);
        return Result.success(config);
    }

    @PostMapping
    @Operation(summary = "新增模型配置")
    public Result<AiModelConfig> create(@RequestBody AiModelConfig config, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        AiModelConfig result = aiModelConfigService.create(config, userId);
        return Result.success(result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模型配置")
    public Result<AiModelConfig> update(@PathVariable Long id, @RequestBody AiModelConfig config) {
        AiModelConfig result = aiModelConfigService.update(id, config);
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型配置")
    public Result<Void> delete(@PathVariable Long id) {
        aiModelConfigService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "设为默认模型")
    public Result<Void> setDefault(@PathVariable Long id) {
        aiModelConfigService.setDefault(id);
        return Result.success();
    }

    @GetMapping("/default")
    @Operation(summary = "获取默认模型")
    public Result<AiModelConfig> getDefault() {
        AiModelConfig config = aiModelConfigService.getDefault();
        return Result.success(config);
    }
}
