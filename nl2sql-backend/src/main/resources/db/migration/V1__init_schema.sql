-- NL2SQL System Database Schema
-- Version: 1.0.0

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（加密）',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    nickname VARCHAR(50) COMMENT '昵称',
    status INT DEFAULT 1 COMMENT '状态 1启用 0禁用',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 数据源配置表
CREATE TABLE IF NOT EXISTS data_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '数据源名称',
    type VARCHAR(20) NOT NULL COMMENT '数据库类型：MySQL/PostgreSQL/Oracle等',
    host VARCHAR(255) NOT NULL COMMENT '主机地址',
    port INT NOT NULL COMMENT '端口',
    database_name VARCHAR(100) NOT NULL COMMENT '数据库名',
    username VARCHAR(100) COMMENT '用户名',
    password VARCHAR(255) COMMENT '密码（加密）',
    status INT DEFAULT 1 COMMENT '状态 1正常 0异常',
    config JSON COMMENT '扩展配置',
    create_by BIGINT COMMENT '创建人',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_type (type),
    INDEX idx_create_by (create_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源配置表';

-- 表元数据
CREATE TABLE IF NOT EXISTS table_meta (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ds_id BIGINT NOT NULL COMMENT '数据源ID',
    table_name VARCHAR(100) NOT NULL COMMENT '表名',
    table_comment VARCHAR(500) COMMENT '表注释',
    custom_comment TEXT COMMENT '自定义描述（业务含义）',
    table_type VARCHAR(20) COMMENT '表类型：TABLE/VIEW',
    row_count BIGINT COMMENT '行数统计',
    sync_status INT DEFAULT 1 COMMENT '同步状态 1已同步 0待同步',
    sync_time TIMESTAMP COMMENT '最后同步时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ds_table (ds_id, table_name),
    INDEX idx_ds_id (ds_id),
    FOREIGN KEY (ds_id) REFERENCES data_source(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表元数据';

-- 字段元数据
CREATE TABLE IF NOT EXISTS field_meta (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    table_id BIGINT NOT NULL COMMENT '表ID',
    field_name VARCHAR(100) NOT NULL COMMENT '字段名',
    field_type VARCHAR(50) NOT NULL COMMENT '字段类型',
    field_comment VARCHAR(500) COMMENT '字段注释',
    custom_comment TEXT COMMENT '自定义描述（业务含义）',
    is_primary INT DEFAULT 0 COMMENT '是否主键',
    is_nullable INT DEFAULT 1 COMMENT '是否可空',
    default_value VARCHAR(255) COMMENT '默认值',
    field_index INT COMMENT '字段顺序',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_table_id (table_id),
    INDEX idx_field_name (field_name),
    FOREIGN KEY (table_id) REFERENCES table_meta(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段元数据';

-- 对话会话表
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    ds_id BIGINT NOT NULL COMMENT '数据源ID',
    title VARCHAR(200) COMMENT '会话标题',
    context_data TEXT COMMENT '上下文数据（JSON）',
    turn_count INT DEFAULT 0 COMMENT '对话轮次',
    status INT DEFAULT 1 COMMENT '状态 1进行中 0已结束',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expired_at TIMESTAMP COMMENT '过期时间',
    INDEX idx_user_id (user_id),
    INDEX idx_ds_id (ds_id),
    INDEX idx_status (status),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (ds_id) REFERENCES data_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

-- 查询历史记录
CREATE TABLE IF NOT EXISTS query_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    ds_id BIGINT NOT NULL COMMENT '数据源ID',
    conversation_id BIGINT COMMENT '会话ID',
    natural_query TEXT NOT NULL COMMENT '自然语言问题',
    generated_sql TEXT COMMENT '生成的SQL',
    execution_status VARCHAR(20) COMMENT '执行状态：SUCCESS/FAILED',
    result_rows INT COMMENT '结果行数',
    execution_time_ms INT COMMENT '执行耗时（毫秒）',
    error_message TEXT COMMENT '错误信息',
    is_favorite INT DEFAULT 0 COMMENT '是否收藏',
    is_from_cache INT DEFAULT 0 COMMENT '是否来自缓存',
    llm_model VARCHAR(50) COMMENT '使用的模型',
    llm_tokens INT COMMENT 'Token消耗',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_ds_id (ds_id),
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_create_time (create_time),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (ds_id) REFERENCES data_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查询历史记录';

-- 收藏夹表
CREATE TABLE IF NOT EXISTS favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    history_id BIGINT NOT NULL COMMENT '历史记录ID',
    name VARCHAR(200) COMMENT '收藏名称',
    description TEXT COMMENT '描述',
    tags VARCHAR(500) COMMENT '标签（逗号分隔）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (history_id) REFERENCES query_history(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏夹';

-- 插入默认管理员用户 (密码: admin123)
INSERT INTO sys_user (username, password, email, nickname, status)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin@nl2sql.com', '管理员', 1)
ON DUPLICATE KEY UPDATE username = username;
