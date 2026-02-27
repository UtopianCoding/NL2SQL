-- 同步任务表
CREATE TABLE IF NOT EXISTS sync_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ds_id BIGINT NOT NULL COMMENT '数据源ID',
    task_type VARCHAR(50) NOT NULL COMMENT '任务类型',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
    total_count INT DEFAULT 0 COMMENT '总数量',
    current_count INT DEFAULT 0 COMMENT '当前处理数量',
    current_table VARCHAR(100) COMMENT '当前处理的表名',
    error_message TEXT COMMENT '错误信息',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ds_id (ds_id),
    INDEX idx_status (status),
    FOREIGN KEY (ds_id) REFERENCES data_source(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步任务表';
