package com.nl2sql.service.vector.impl;

import com.nl2sql.model.entity.FieldMeta;
import com.nl2sql.model.entity.TableMeta;
import com.nl2sql.service.embedding.EmbeddingProvider;
import com.nl2sql.service.vector.SchemaVectorizationService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.highlevel.collection.ListCollectionsParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.DescCollResponseWrapper;
import io.milvus.response.GetCollStatResponseWrapper;
import io.milvus.grpc.DescribeCollectionResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.util.*;

/**
 * Schema 向量化服务实现
 * 将表和字段的元数据向量化并存储到 Milvus
 */
@Slf4j
@Service
public class SchemaVectorizationServiceImpl implements SchemaVectorizationService {

    private static final String TABLE_COLLECTION = "table_vectors";
    private static final String FIELD_COLLECTION = "field_vectors";

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private EmbeddingProvider embeddingProvider;

    @Value("${milvus.embedding.dimension:768}")
    private int dimension;

    @PostConstruct
    public void init() {
        try {
            createCollectionsIfNotExist();
            log.info("Schema 向量化服务初始化完成");
        } catch (Exception e) {
            log.error("Schema 向量化服务初始化失败: {}", e.getMessage(), e);
        }
    }

    private void createCollectionsIfNotExist() {
        createTableCollectionIfNotExist();
        createFieldCollectionIfNotExist();
    }

    private void createTableCollectionIfNotExist() {
        try {
            R<Boolean> hasCollection = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(TABLE_COLLECTION)
                            .build()
            );

            if (hasCollection.getData() != null && hasCollection.getData()) {
                // 检查维度是否匹配
                if (!isDimensionMatch(TABLE_COLLECTION, dimension)) {
                    log.warn("表向量集合维度不匹配，删除并重建: {}", TABLE_COLLECTION);
                    dropCollection(TABLE_COLLECTION);
                } else {
                    log.debug("表向量集合已存在: {}", TABLE_COLLECTION);
                    return;
                }
            }

            // 创建集合
            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(true)
                    .build();

            FieldType vectorField = FieldType.newBuilder()
                    .withName("vector")
                    .withDataType(DataType.FloatVector)
                    .withDimension(dimension)
                    .build();

            FieldType tableIdField = FieldType.newBuilder()
                    .withName("table_id")
                    .withDataType(DataType.Int64)
                    .build();

            FieldType dsIdField = FieldType.newBuilder()
                    .withName("ds_id")
                    .withDataType(DataType.Int64)
                    .build();

            FieldType tableNameField = FieldType.newBuilder()
                    .withName("table_name")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(256)
                    .build();

            FieldType tableCommentField = FieldType.newBuilder()
                    .withName("table_comment")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(1024)
                    .build();

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(TABLE_COLLECTION)
                    .withDescription("Table metadata vectors")
                    .withShardsNum(2)
                    .addFieldType(idField)
                    .addFieldType(vectorField)
                    .addFieldType(tableIdField)
                    .addFieldType(dsIdField)
                    .addFieldType(tableNameField)
                    .addFieldType(tableCommentField)
                    .build();

            R<RpcStatus> createResult = milvusClient.createCollection(createParam);
            if (createResult.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("创建表向量集合失败: " + createResult.getMessage());
            }

            // 创建索引
            createVectorIndex(TABLE_COLLECTION, "vector");

            log.info("表向量集合创建成功: {}", TABLE_COLLECTION);

        } catch (Exception e) {
            log.error("创建表向量集合失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建表向量集合失败", e);
        }
    }

    private void createFieldCollectionIfNotExist() {
        try {
            R<Boolean> hasCollection = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(FIELD_COLLECTION)
                            .build()
            );

            if (hasCollection.getData() != null && hasCollection.getData()) {
                // 检查维度是否匹配
                if (!isDimensionMatch(FIELD_COLLECTION, dimension)) {
                    log.warn("字段向量集合维度不匹配，删除并重建: {}", FIELD_COLLECTION);
                    dropCollection(FIELD_COLLECTION);
                } else {
                    log.debug("字段向量集合已存在: {}", FIELD_COLLECTION);
                    return;
                }
            }

            // 创建集合
            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(true)
                    .build();

            FieldType vectorField = FieldType.newBuilder()
                    .withName("vector")
                    .withDataType(DataType.FloatVector)
                    .withDimension(dimension)
                    .build();

            FieldType fieldIdField = FieldType.newBuilder()
                    .withName("field_id")
                    .withDataType(DataType.Int64)
                    .build();

            FieldType tableIdField = FieldType.newBuilder()
                    .withName("table_id")
                    .withDataType(DataType.Int64)
                    .build();

            FieldType dsIdField = FieldType.newBuilder()
                    .withName("ds_id")
                    .withDataType(DataType.Int64)
                    .build();

            FieldType fieldNameField = FieldType.newBuilder()
                    .withName("field_name")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(256)
                    .build();

            FieldType fieldTypeField = FieldType.newBuilder()
                    .withName("field_type")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(128)
                    .build();

            FieldType fieldCommentField = FieldType.newBuilder()
                    .withName("field_comment")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(1024)
                    .build();

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(FIELD_COLLECTION)
                    .withDescription("Field metadata vectors")
                    .withShardsNum(2)
                    .addFieldType(idField)
                    .addFieldType(vectorField)
                    .addFieldType(fieldIdField)
                    .addFieldType(tableIdField)
                    .addFieldType(dsIdField)
                    .addFieldType(fieldNameField)
                    .addFieldType(fieldTypeField)
                    .addFieldType(fieldCommentField)
                    .build();

            R<RpcStatus> createResult = milvusClient.createCollection(createParam);
            if (createResult.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("创建字段向量集合失败: " + createResult.getMessage());
            }

            // 创建索引
            createVectorIndex(FIELD_COLLECTION, "vector");

            log.info("字段向量集合创建成功: {}", FIELD_COLLECTION);

        } catch (Exception e) {
            log.error("创建字段向量集合失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建字段向量集合失败", e);
        }
    }

    private void createVectorIndex(String collectionName, String fieldName) {
        try {
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName(fieldName)
                    .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withExtraParam("{\"nlist\": 128}")
                    .withSyncMode(Boolean.TRUE)
                    .withSyncWaitingInterval(500L)
                    .withSyncWaitingTimeout(30L)
                    .build();

            R<RpcStatus> result = milvusClient.createIndex(indexParam);
            if (result.getStatus() != R.Status.Success.getCode()) {
                log.error("创建索引失败: {}", result.getMessage());
            } else {
                log.info("索引创建成功: {}.{}", collectionName, fieldName);
            }
        } catch (Exception e) {
            log.error("创建索引失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查集合的向量维度是否与配置匹配
     */
    private boolean isDimensionMatch(String collectionName, int expectedDimension) {
        try {
            R<DescribeCollectionResponse> describeResult = milvusClient.describeCollection(
                    DescribeCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );

            if (describeResult.getStatus() != R.Status.Success.getCode()) {
                log.warn("获取集合信息失败: {}", describeResult.getMessage());
                return false;
            }

            // 使用 Wrapper 来获取字段信息
            DescCollResponseWrapper wrapper = new DescCollResponseWrapper(describeResult.getData());
            for (FieldType fieldType : wrapper.getFields()) {
                if ("vector".equals(fieldType.getName())) {
                    int actualDimension = fieldType.getDimension();
                    if (actualDimension != expectedDimension) {
                        log.warn("集合 {} 维度不匹配: 实际={}, 期望={}", 
                                collectionName, actualDimension, expectedDimension);
                        return false;
                    }
                    return true;
                }
            }

            log.warn("集合 {} 中未找到 vector 字段", collectionName);
            return false;

        } catch (Exception e) {
            log.error("检查集合维度失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除集合
     */
    private void dropCollection(String collectionName) {
        try {
            R<RpcStatus> result = milvusClient.dropCollection(
                    DropCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );

            if (result.getStatus() == R.Status.Success.getCode()) {
                log.info("集合删除成功: {}", collectionName);
            } else {
                log.error("集合删除失败: {}", result.getMessage());
            }
        } catch (Exception e) {
            log.error("删除集合 {} 失败: {}", collectionName, e.getMessage(), e);
        }
    }

    @Override
    public void vectorizeTable(TableMeta tableMeta, List<FieldMeta> fields, Long dsId) {
        if (tableMeta == null) {
            log.warn("TableMeta is null, skipping vectorization");
            return;
        }

        try {
            // 1. 生成表的向量
            String tableText = buildTableText(tableMeta, fields);
            float[] tableVector = embeddingProvider.embed(tableText);

            // 2. 存储表的向量
            insertTableVector(tableMeta, dsId, tableVector);

            // 3. 向量化字段
            if (fields != null && !fields.isEmpty()) {
                for (FieldMeta field : fields) {
                    vectorizeField(field, tableMeta.getId(), dsId);
                }
            }

            log.info("表 {} 向量化完成，包含 {} 个字段", tableMeta.getTableName(), 
                fields != null ? fields.size() : 0);

        } catch (Exception e) {
            log.error("表 {} 向量化失败: {}", tableMeta.getTableName(), e.getMessage(), e);
            throw new RuntimeException("表向量化失败: " + tableMeta.getTableName(), e);
        }
    }

    @Override
    public void vectorizeTablesBatch(List<TableVectorizationRequest> tables, Long dsId) {
        if (tables == null || tables.isEmpty()) {
            return;
        }

        log.info("开始批量向量化 {} 个表", tables.size());
        int success = 0;
        int failed = 0;

        for (TableVectorizationRequest request : tables) {
            try {
                vectorizeTable(request.getTableMeta(), request.getFields(), dsId);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("批量向量化表 {} 失败: {}", 
                    request.getTableMeta() != null ? request.getTableMeta().getTableName() : "unknown", 
                    e.getMessage());
            }
        }

        log.info("批量向量化完成，成功: {}, 失败: {}", success, failed);
    }

    @Override
    public void deleteByDsId(Long dsId) {
        try {
            // 删除表的向量
            String tableDeleteExpr = String.format("ds_id == %d", dsId);
            DeleteParam tableDeleteParam = DeleteParam.newBuilder()
                    .withCollectionName(TABLE_COLLECTION)
                    .withExpr(tableDeleteExpr)
                    .build();
            R<MutationResult> tableDeleteResult = milvusClient.delete(tableDeleteParam);

            // 删除字段的向量
            String fieldDeleteExpr = String.format("ds_id == %d", dsId);
            DeleteParam fieldDeleteParam = DeleteParam.newBuilder()
                    .withCollectionName(FIELD_COLLECTION)
                    .withExpr(fieldDeleteExpr)
                    .build();
            R<MutationResult> fieldDeleteResult = milvusClient.delete(fieldDeleteParam);

            log.info("删除数据源 {} 的向量数据完成", dsId);

        } catch (Exception e) {
            log.error("删除数据源 {} 的向量数据失败: {}", dsId, e.getMessage(), e);
            throw new RuntimeException("删除向量数据失败", e);
        }
    }

    @Override
    public void deleteByTableId(Long tableId) {
        try {
            // 删除表的向量
            String tableDeleteExpr = String.format("table_id == %d", tableId);
            DeleteParam tableDeleteParam = DeleteParam.newBuilder()
                    .withCollectionName(TABLE_COLLECTION)
                    .withExpr(tableDeleteExpr)
                    .build();
            R<MutationResult> tableDeleteResult = milvusClient.delete(tableDeleteParam);

            // 删除该表的所有字段向量
            String fieldDeleteExpr = String.format("table_id == %d", tableId);
            DeleteParam fieldDeleteParam = DeleteParam.newBuilder()
                    .withCollectionName(FIELD_COLLECTION)
                    .withExpr(fieldDeleteExpr)
                    .build();
            R<MutationResult> fieldDeleteResult = milvusClient.delete(fieldDeleteParam);

            log.info("删除表 {} 的向量数据完成", tableId);

        } catch (Exception e) {
            log.error("删除表 {} 的向量数据失败: {}", tableId, e.getMessage(), e);
            throw new RuntimeException("删除向量数据失败", e);
        }
    }

    private String buildTableText(TableMeta tableMeta, List<FieldMeta> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("表名: ").append(tableMeta.getTableName());
        if (tableMeta.getTableComment() != null && !tableMeta.getTableComment().isEmpty()) {
            sb.append(", 描述: ").append(tableMeta.getTableComment());
        }
        if (tableMeta.getCustomComment() != null && !tableMeta.getCustomComment().isEmpty()) {
            sb.append(", 业务含义: ").append(tableMeta.getCustomComment());
        }

        if (fields != null && !fields.isEmpty()) {
            sb.append(". 包含字段: ");
            for (int i = 0; i < Math.min(fields.size(), 20); i++) { // 最多取20个字段
                FieldMeta field = fields.get(i);
                if (i > 0) sb.append(", ");
                sb.append(field.getFieldName());
                if (field.getFieldComment() != null && !field.getFieldComment().isEmpty()) {
                    sb.append("(").append(field.getFieldComment()).append(")");
                }
            }
            if (fields.size() > 20) {
                sb.append(" 等").append(fields.size()).append("个字段");
            }
        }

        return sb.toString();
    }

    private String buildFieldText(FieldMeta field) {
        StringBuilder sb = new StringBuilder();
        sb.append("字段名: ").append(field.getFieldName());
        sb.append(", 类型: ").append(field.getFieldType());
        if (field.getFieldComment() != null && !field.getFieldComment().isEmpty()) {
            sb.append(", 描述: ").append(field.getFieldComment());
        }
        if (field.getCustomComment() != null && !field.getCustomComment().isEmpty()) {
            sb.append(", 业务含义: ").append(field.getCustomComment());
        }
        if (field.getIsPrimary() != null && field.getIsPrimary() == 1) {
            sb.append(", 主键");
        }
        return sb.toString();
    }

    private void vectorizeField(FieldMeta field, Long tableId, Long dsId) {
        try {
            String fieldText = buildFieldText(field);
            float[] fieldVector = embeddingProvider.embed(fieldText);

            insertFieldVector(field, tableId, dsId, fieldVector);

        } catch (Exception e) {
            log.error("字段 {} 向量化失败: {}", field.getFieldName(), e.getMessage());
            // 字段向量化失败不影响整体流程
        }
    }

    private void insertTableVector(TableMeta table, Long dsId, float[] vector) {
        try {
            List<InsertParam.Field> fields = new ArrayList<>();

            // vector字段
            List<List<Float>> vectors = new ArrayList<>();
            List<Float> vectorList = new ArrayList<>();
            for (float v : vector) {
                vectorList.add(v);
            }
            vectors.add(vectorList);
            fields.add(new InsertParam.Field("vector", vectors));

            // table_id字段
            fields.add(new InsertParam.Field("table_id", Collections.singletonList(table.getId())));

            // ds_id字段
            fields.add(new InsertParam.Field("ds_id", Collections.singletonList(dsId)));

            // table_name字段
            fields.add(new InsertParam.Field("table_name", Collections.singletonList(table.getTableName())));

            // table_comment字段
            String comment = table.getTableComment() != null ? table.getTableComment() : "";
            fields.add(new InsertParam.Field("table_comment", Collections.singletonList(comment)));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(TABLE_COLLECTION)
                    .withFields(fields)
                    .build();

            R<MutationResult> result = milvusClient.insert(insertParam);
            if (result.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("插入表向量失败: " + result.getMessage());
            }

            log.debug("表 {} 向量插入成功", table.getTableName());

        } catch (Exception e) {
            log.error("插入表 {} 向量失败: {}", table.getTableName(), e.getMessage());
            throw new RuntimeException("插入表向量失败", e);
        }
    }

    private void insertFieldVector(FieldMeta field, Long tableId, Long dsId, float[] vector) {
        try {
            List<InsertParam.Field> fields = new ArrayList<>();

            // vector字段
            List<List<Float>> vectors = new ArrayList<>();
            List<Float> vectorList = new ArrayList<>();
            for (float v : vector) {
                vectorList.add(v);
            }
            vectors.add(vectorList);
            fields.add(new InsertParam.Field("vector", vectors));

            // field_id字段
            fields.add(new InsertParam.Field("field_id", Collections.singletonList(field.getId())));

            // table_id字段
            fields.add(new InsertParam.Field("table_id", Collections.singletonList(tableId)));

            // ds_id字段
            fields.add(new InsertParam.Field("ds_id", Collections.singletonList(dsId)));

            // field_name字段
            fields.add(new InsertParam.Field("field_name", Collections.singletonList(field.getFieldName())));

            // field_type字段
            fields.add(new InsertParam.Field("field_type", Collections.singletonList(field.getFieldType())));

            // field_comment字段
            String comment = field.getFieldComment() != null ? field.getFieldComment() : "";
            fields.add(new InsertParam.Field("field_comment", Collections.singletonList(comment)));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(FIELD_COLLECTION)
                    .withFields(fields)
                    .build();

            R<MutationResult> result = milvusClient.insert(insertParam);
            if (result.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("插入字段向量失败: " + result.getMessage());
            }

            log.debug("字段 {} 向量插入成功", field.getFieldName());

        } catch (Exception e) {
            log.error("插入字段 {} 向量失败: {}", field.getFieldName(), e.getMessage());
            throw new RuntimeException("插入字段向量失败", e);
        }
    }
}
