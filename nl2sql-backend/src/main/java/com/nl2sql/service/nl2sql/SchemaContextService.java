package com.nl2sql.service.nl2sql;

import java.util.List;

/**
 * Schema 上下文服务接口
 * 提供构建全量和精简 Schema 的功能
 */
public interface SchemaContextService {

    /**
     * 构建全量 Schema 上下文
     * 包含数据源中的所有表和字段
     *
     * @param dsId 数据源ID
     * @return Schema 描述字符串
     */
    String buildFullSchemaContext(Long dsId);

    /**
     * 构建精简 Schema 上下文
     * 根据用户问题通过向量检索筛选相关表和字段
     *
     * @param question 用户问题
     * @param dsId     数据源ID
     * @return 精简的 Schema 描述字符串
     */
    String buildCompactSchemaContext(String question, Long dsId);

    /**
     * 根据指定的表列表构建 Schema 上下文
     *
     * @param tableIds 表ID列表
     * @param dsId     数据源ID
     * @return Schema 描述字符串
     */
    String buildSchemaWithSelectedTables(List<Long> tableIds, Long dsId);
}
