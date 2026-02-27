package com.nl2sql.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DataSourceDTO {

    private Long id;

    @NotBlank(message = "连接名称不能为空")
    private String name;

    @NotBlank(message = "数据库类型不能为空")
    private String type;

    @NotBlank(message = "主机地址不能为空")
    private String host;

    @NotNull(message = "端口不能为空")
    private Integer port;

    @NotBlank(message = "数据库名不能为空")
    private String databaseName;

    private String username;

    private String password;

    private String config;
}
