package com.nl2sql.service.aimodel.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nl2sql.mapper.AiModelConfigMapper;
import com.nl2sql.model.entity.AiModelConfig;
import com.nl2sql.service.aimodel.AiModelConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiModelConfigServiceImpl implements AiModelConfigService {

    @Autowired
    private AiModelConfigMapper aiModelConfigMapper;

    @Override
    public List<AiModelConfig> list(String keyword) {
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AiModelConfig::getModelName, keyword)
                   .or().like(AiModelConfig::getProviderName, keyword)
                   .or().like(AiModelConfig::getBaseModel, keyword);
        }
        wrapper.orderByDesc(AiModelConfig::getIsDefault)
               .orderByDesc(AiModelConfig::getCreateTime);
        return aiModelConfigMapper.selectList(wrapper);
    }

    @Override
    public AiModelConfig getById(Long id) {
        return aiModelConfigMapper.selectById(id);
    }

    @Override
    @Transactional
    public AiModelConfig create(AiModelConfig config, Long userId) {
        config.setCreateBy(userId);
        if (config.getIsDefault() == null) {
            config.setIsDefault(0);
        }
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
        // 如果设为默认，先取消其他默认
        if (config.getIsDefault() == 1) {
            clearDefault();
        }
        aiModelConfigMapper.insert(config);
        return config;
    }

    @Override
    @Transactional
    public AiModelConfig update(Long id, AiModelConfig config) {
        AiModelConfig existing = aiModelConfigMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("模型配置不存在");
        }
        config.setId(id);
        // 如果设为默认，先取消其他默认
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            clearDefault();
        }
        aiModelConfigMapper.updateById(config);
        return aiModelConfigMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        aiModelConfigMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        AiModelConfig config = aiModelConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("模型配置不存在");
        }
        clearDefault();
        config.setIsDefault(1);
        aiModelConfigMapper.updateById(config);
    }

    @Override
    public AiModelConfig getDefault() {
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelConfig::getIsDefault, 1).last("LIMIT 1");
        return aiModelConfigMapper.selectOne(wrapper);
    }

    private void clearDefault() {
        LambdaUpdateWrapper<AiModelConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiModelConfig::getIsDefault, 1)
               .set(AiModelConfig::getIsDefault, 0);
        aiModelConfigMapper.update(null, wrapper);
    }
}
