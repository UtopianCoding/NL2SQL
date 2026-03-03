package com.nl2sql.service.aimodel;

import com.nl2sql.model.entity.AiModelConfig;

import java.util.List;

public interface AiModelConfigService {

    List<AiModelConfig> list(String keyword);

    AiModelConfig getById(Long id);

    AiModelConfig create(AiModelConfig config, Long userId);

    AiModelConfig update(Long id, AiModelConfig config);

    void delete(Long id);

    void setDefault(Long id);

    AiModelConfig getDefault();
}
