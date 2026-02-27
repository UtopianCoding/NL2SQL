-- 聊天消息表：保存智能问数页面的完整对话记录
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user/assistant',
    content TEXT COMMENT '消息文本内容',
    sql_text TEXT COMMENT '生成的SQL（仅assistant）',
    result_data JSON COMMENT '查询结果数据（仅assistant，JSON数组）',
    result_rows INT COMMENT '结果行数',
    execution_time_ms INT COMMENT '执行耗时（毫秒）',
    error_message TEXT COMMENT '错误信息',
    history_id BIGINT COMMENT '关联的query_history记录ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time),
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';
