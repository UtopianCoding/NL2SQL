-- AI模型配置表
CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称（用户自定义）',
    provider VARCHAR(50) NOT NULL COMMENT '供应商标识（如deepseek, aliyun, qianfan等）',
    provider_name VARCHAR(100) NOT NULL COMMENT '供应商显示名称',
    model_type VARCHAR(50) NOT NULL DEFAULT 'LLM' COMMENT '模型类型（LLM/Embedding等）',
    base_model VARCHAR(100) NOT NULL COMMENT '基础模型名称（如deepseek-chat, qwen-plus）',
    api_url VARCHAR(500) NOT NULL COMMENT 'API域名',
    api_key VARCHAR(500) NOT NULL COMMENT 'API Key',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否为默认模型 0-否 1-是',
    params TEXT COMMENT '高级参数JSON（如temperature等）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';
