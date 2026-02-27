package com.nl2sql.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long userId;

    /** user / assistant */
    private String role;

    private String content;

    private String sqlText;

    private String resultData;

    private Integer resultRows;

    private Integer executionTimeMs;

    private String errorMessage;

    private Long historyId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
