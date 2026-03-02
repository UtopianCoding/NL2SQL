-- 表关系持久化表
CREATE TABLE table_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ds_id BIGINT NOT NULL COMMENT '数据源ID',
    source_table_id BIGINT NOT NULL COMMENT '源表ID',
    source_table_name VARCHAR(200) COMMENT '源表名',
    target_table_id BIGINT NOT NULL COMMENT '目标表ID',
    target_table_name VARCHAR(200) COMMENT '目标表名',
    relation_type VARCHAR(50) NOT NULL COMMENT '关系类型: ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY',
    source_fields VARCHAR(500) DEFAULT '' COMMENT '源字段，逗号分隔',
    target_fields VARCHAR(500) DEFAULT '' COMMENT '目标字段，逗号分隔',
    confidence DOUBLE DEFAULT 0.8 COMMENT '置信度',
    reasoning VARCHAR(1000) COMMENT '推断依据',
    created_by VARCHAR(50) DEFAULT 'manual' COMMENT '创建来源: manual, ai',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ds_id (ds_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表关系持久化';
