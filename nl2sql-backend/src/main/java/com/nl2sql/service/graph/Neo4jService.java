package com.nl2sql.service.graph;

import com.nl2sql.model.entity.FieldMeta;
import com.nl2sql.model.entity.TableMeta;
import com.nl2sql.model.graph.TableNode;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Neo4j 图数据库服务接口
 * 负责管理表和字段的图数据，以及构建 Schema 上下文
 */
public interface Neo4jService {

    /**
     * 同步表和字段到图数据库
     *
     * @param tableMeta 表元数据
     * @param fields    字段列表
     */
    void syncTableToGraph(TableMeta tableMeta, List<FieldMeta> fields);

    /**
     * 创建外键关系
     *
     * @param sourceTableId 源表ID
     * @param targetTableId 目标表ID
     * @param sourceField   源字段
     * @param targetField   目标字段
     */
    void createForeignKeyRelation(Long sourceTableId, Long targetTableId,
                                    String sourceField, String targetField);

    /**
     * 更新连接关系
     *
     * @param sourceTableId 源表ID
     * @param targetTableId 目标表ID
     * @param joinCondition 连接条件
     */
    void updateJoinRelation(Long sourceTableId, Long targetTableId, String joinCondition);

    /**
     * 获取关联表
     *
     * @param tableId 表ID
     * @return 关联表列表
     */
    List<TableNode> getRelatedTables(Long tableId);

    /**
     * 根据数据源ID获取表列表
     *
     * @param dsId 数据源ID
     * @return 表列表
     */
    List<TableNode> getTablesByDsId(Long dsId);

    /**
     * 根据数据源ID删除所有数据
     *
     * @param dsId 数据源ID
     */
    void deleteByDsId(Long dsId);

    /**
     * 删除表节点
     *
     * @param tableId 表ID
     */
    void deleteTableNode(Long tableId);

    /**
     * 构建完整的数据库 Schema 上下文
     * 包含所有表和字段信息
     *
     * @param dsId 数据源ID
     * @return Schema 上下文字符串
     * @deprecated 请使用 SchemaContextService.buildFullSchemaContext
     */
    @Deprecated
    String buildSchemaContext(Long dsId);

    /**
     * 根据指定的表列表构建 Schema 上下文
     *
     * @param tableIds 表ID集合
     * @param dsId     数据源ID
     * @return Schema 上下文字符串
     */
    String buildSchemaContextWithTables(Collection<Long> tableIds, Long dsId);

    /**
     * 构建精简的 Schema 上下文
     * 只包含相关表的简要信息
     *
     * @param tableIds 相关表ID集合
     * @param dsId     数据源ID
     * @return Schema 上下文字符串
     */
    String buildCompactSchemaContext(Collection<Long> tableIds, Long dsId);
}
