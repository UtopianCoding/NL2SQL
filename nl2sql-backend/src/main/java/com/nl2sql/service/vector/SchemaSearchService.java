package com.nl2sql.service.vector;

import com.nl2sql.model.graph.FieldNode;
import com.nl2sql.model.graph.TableNode;

import java.util.List;

/**
 * Schema 向量检索服务接口
 * 负责根据用户查询进行向量检索，筛选出最相关的表和字段
 */
public interface SchemaSearchService {

    /**
     * 根据用户问题检索相关表
     *
     * @param question 用户问题
     * @param dsId     数据源ID
     * @param topK     返回的最相关表数量
     * @return 相关表列表，按相关度排序
     */
    List<TableSearchResult> searchRelevantTables(String question, Long dsId, int topK);

    /**
     * 根据用户问题检索相关表和字段
     *
     * @param question 用户问题
     * @param dsId     数据源ID
     * @param topK     返回的最相关表数量
     * @param topKFields 每张表返回的最相关字段数量
     * @return 相关表和字段的映射
     */
    TableFieldSearchResult searchRelevantTablesAndFields(String question, Long dsId, int topK, int topKFields);

    /**
     * 根据SQL查询检索相关表（用于SQL修正场景）
     *
     * @param sql  SQL语句
     * @param dsId 数据源ID
     * @param topK 返回的最相关表数量
     * @return 相关表列表
     */
    List<TableSearchResult> searchRelevantTablesBySql(String sql, Long dsId, int topK);

    /**
     * 表搜索结果
     */
    class TableSearchResult {
        private TableNode table;
        private float score;
        private List<FieldSearchResult> relevantFields;

        public TableSearchResult(TableNode table, float score) {
            this.table = table;
            this.score = score;
        }

        public TableNode getTable() {
            return table;
        }

        public void setTable(TableNode table) {
            this.table = table;
        }

        public float getScore() {
            return score;
        }

        public void setScore(float score) {
            this.score = score;
        }

        public List<FieldSearchResult> getRelevantFields() {
            return relevantFields;
        }

        public void setRelevantFields(List<FieldSearchResult> relevantFields) {
            this.relevantFields = relevantFields;
        }
    }

    /**
     * 字段搜索结果
     */
    class FieldSearchResult {
        private FieldNode field;
        private float score;

        public FieldSearchResult(FieldNode field, float score) {
            this.field = field;
            this.score = score;
        }

        public FieldNode getField() {
            return field;
        }

        public void setField(FieldNode field) {
            this.field = field;
        }

        public float getScore() {
            return score;
        }

        public void setScore(float score) {
            this.score = score;
        }
    }

    /**
     * 表和字段搜索结果
     */
    class TableFieldSearchResult {
        private List<TableSearchResult> tables;
        private List<Long> relatedTableIds; // 关联的表ID（通过外键关系）

        public TableFieldSearchResult(List<TableSearchResult> tables) {
            this.tables = tables;
        }

        public List<TableSearchResult> getTables() {
            return tables;
        }

        public void setTables(List<TableSearchResult> tables) {
            this.tables = tables;
        }

        public List<Long> getRelatedTableIds() {
            return relatedTableIds;
        }

        public void setRelatedTableIds(List<Long> relatedTableIds) {
            this.relatedTableIds = relatedTableIds;
        }
    }
}
