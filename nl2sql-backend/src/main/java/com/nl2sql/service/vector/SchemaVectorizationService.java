package com.nl2sql.service.vector;

import com.nl2sql.model.entity.FieldMeta;
import com.nl2sql.model.entity.TableMeta;

import java.util.List;

/**
 * Schema 向量化服务接口
 * 负责将表和字段的元数据转换为向量并存储到 Milvus
 */
public interface SchemaVectorizationService {

    /**
     * 向量化单个表（包含其所有字段）
     *
     * @param tableMeta 表元数据
     * @param fields    字段列表
     * @param dsId      数据源ID
     */
    void vectorizeTable(TableMeta tableMeta, List<FieldMeta> fields, Long dsId);

    /**
     * 批量向量化多个表
     *
     * @param tables 表元数据列表，每个元素包含 TableMeta 和对应的 FieldMeta 列表
     * @param dsId   数据源ID
     */
    void vectorizeTablesBatch(List<TableVectorizationRequest> tables, Long dsId);

    /**
     * 删除指定数据源的向量化数据
     *
     * @param dsId 数据源ID
     */
    void deleteByDsId(Long dsId);

    /**
     * 删除指定表的向量化数据
     *
     * @param tableId 表ID
     */
    void deleteByTableId(Long tableId);

    /**
     * 表向量化请求对象
     */
    class TableVectorizationRequest {
        private TableMeta tableMeta;
        private List<FieldMeta> fields;

        public TableVectorizationRequest(TableMeta tableMeta, List<FieldMeta> fields) {
            this.tableMeta = tableMeta;
            this.fields = fields;
        }

        public TableMeta getTableMeta() {
            return tableMeta;
        }

        public void setTableMeta(TableMeta tableMeta) {
            this.tableMeta = tableMeta;
        }

        public List<FieldMeta> getFields() {
            return fields;
        }

        public void setFields(List<FieldMeta> fields) {
            this.fields = fields;
        }
    }
}
