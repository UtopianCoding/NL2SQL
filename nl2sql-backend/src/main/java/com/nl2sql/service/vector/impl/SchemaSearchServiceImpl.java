package com.nl2sql.service.vector.impl;

import com.nl2sql.model.graph.FieldNode;
import com.nl2sql.model.graph.TableNode;
import com.nl2sql.repository.neo4j.FieldNodeRepository;
import com.nl2sql.repository.neo4j.TableNodeRepository;
import com.nl2sql.service.embedding.EmbeddingProvider;
import com.nl2sql.service.vector.SchemaSearchService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Schema 向量检索服务实现
 * 根据用户查询进行向量检索，筛选出最相关的表和字段
 */
@Slf4j
@Service
public class SchemaSearchServiceImpl implements SchemaSearchService {

    private static final String TABLE_COLLECTION = "table_vectors";
    private static final String FIELD_COLLECTION = "field_vectors";

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private EmbeddingProvider embeddingProvider;

    @Autowired
    private TableNodeRepository tableNodeRepository;

    @Autowired
    private FieldNodeRepository fieldNodeRepository;

    @Value("${nl2sql.vector.topk:10}")
    private int defaultTopK;

    @Value("${nl2sql.vector.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Override
    public List<TableSearchResult> searchRelevantTables(String question, Long dsId, int topK) {
        if (question == null || question.trim().isEmpty()) {
            log.warn("查询问题为空，返回空结果");
            return Collections.emptyList();
        }

        try {
            // 1. 将问题转换为向量
            float[] questionVector = embeddingProvider.embed(question);

            // 2. 在 Milvus 中搜索相似的表向量
            List<Long> tableIds = searchTableVectors(questionVector, dsId, topK);

            // 3. 从 Neo4j 获取表详细信息
            List<TableSearchResult> results = new ArrayList<>();
            for (int i = 0; i < tableIds.size(); i++) {
                Long tableId = tableIds.get(i);
                Optional<TableNode> tableNodeOpt = tableNodeRepository.findById(tableId);
                
                if (tableNodeOpt.isPresent()) {
                    TableNode tableNode = tableNodeOpt.get();
                    // 计算相似度分数（根据排名递减）
                    float score = 1.0f - (float) i / topK;
                    results.add(new TableSearchResult(tableNode, score));
                }
            }

            log.info("表向量检索完成，查询: '{}', 找到 {} 个相关表", question, results.size());
            return results;

        } catch (Exception e) {
            log.error("表向量检索失败: {}", e.getMessage(), e);
            // 失败时返回空列表，让调用方使用默认的全表Schema
            return Collections.emptyList();
        }
    }

    @Override
    public TableFieldSearchResult searchRelevantTablesAndFields(String question, Long dsId, int topK, int topKFields) {
        // 1. 先搜索相关表
        List<TableSearchResult> tableResults = searchRelevantTables(question, dsId, topK);

        if (tableResults.isEmpty()) {
            return new TableFieldSearchResult(Collections.emptyList());
        }

        // 2. 对每个表搜索相关字段
        try {
            float[] questionVector = embeddingProvider.embed(question);

            for (TableSearchResult tableResult : tableResults) {
                Long tableId = tableResult.getTable().getTableId();
                List<FieldSearchResult> relevantFields = searchFieldVectors(
                    questionVector, tableId, dsId, topKFields);
                tableResult.setRelevantFields(relevantFields);
            }

        } catch (Exception e) {
            log.error("字段向量检索失败: {}", e.getMessage());
        }

        // 3. 查找关联表（通过外键关系）
        List<Long> relatedTableIds = findRelatedTables(tableResults, dsId);

        TableFieldSearchResult result = new TableFieldSearchResult(tableResults);
        result.setRelatedTableIds(relatedTableIds);
        return result;
    }

    @Override
    public List<TableSearchResult> searchRelevantTablesBySql(String sql, Long dsId, int topK) {
        // 从SQL中提取关键信息作为查询文本
        String extractedQuery = extractQueryFromSql(sql);
        return searchRelevantTables(extractedQuery, dsId, topK);
    }

    private List<Long> searchTableVectors(float[] vector, Long dsId, int topK) {
        try {
            // 构建过滤表达式
            String expr = String.format("ds_id == %d", dsId);

            List<String> outputFields = Arrays.asList("table_id", "ds_id");

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(TABLE_COLLECTION)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withExpr(expr)
                    .withVectors(Collections.singletonList(vector))
                    .withVectorFieldName("vector")
                    .withTopK(topK)
                    .withOutFields(outputFields)
                    .build();

            R<SearchResults> searchResult = milvusClient.search(searchParam);
            if (searchResult.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("搜索表向量失败: " + searchResult.getMessage());
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResult.getData().getResults());
            List<Long> tableIds = new ArrayList<>();

            // 获取第一组搜索结果（因为我们的查询向量只有一个）
            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            for (SearchResultsWrapper.IDScore score : scores) {
                // 从输出字段中获取 table_id
                Map<String, Object> fieldData = (Map<String, Object>) score.get("fields");
                if (fieldData != null && fieldData.containsKey("table_id")) {
                    Long tableId = (Long) fieldData.get("table_id");
                    tableIds.add(tableId);
                }
            }

            return tableIds;

        } catch (Exception e) {
            log.error("搜索表向量失败: {}", e.getMessage(), e);
            throw new RuntimeException("搜索表向量失败", e);
        }
    }

    private List<FieldSearchResult> searchFieldVectors(float[] vector, Long tableId, Long dsId, int topK) {
        try {
            // 构建过滤表达式
            String expr = String.format("table_id == %d && ds_id == %d", tableId, dsId);

            List<String> outputFields = Arrays.asList("field_id", "table_id", "ds_id", "field_name", 
                "field_type", "field_comment");

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(FIELD_COLLECTION)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withExpr(expr)
                    .withVectors(Collections.singletonList(vector))
                    .withVectorFieldName("vector")
                    .withTopK(topK)
                    .withOutFields(outputFields)
                    .build();

            R<SearchResults> searchResult = milvusClient.search(searchParam);
            if (searchResult.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("搜索字段向量失败: " + searchResult.getMessage());
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResult.getData().getResults());
            List<FieldSearchResult> results = new ArrayList<>();

            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            for (SearchResultsWrapper.IDScore score : scores) {
                Map<String, Object> fieldData = (Map<String, Object>) score.get("fields");
                if (fieldData != null) {
                    // 构建 FieldNode
                    FieldNode fieldNode = new FieldNode();
                    fieldNode.setFieldId((Long) fieldData.get("field_id"));
                    fieldNode.setTableId((Long) fieldData.get("table_id"));
                    fieldNode.setFieldName((String) fieldData.get("field_name"));
                    fieldNode.setFieldType((String) fieldData.get("field_type"));
                    fieldNode.setFieldComment((String) fieldData.get("field_comment"));

                    results.add(new FieldSearchResult(fieldNode, (float) score.getScore()));
                }
            }

            return results;

        } catch (Exception e) {
            log.error("搜索字段向量失败: {}", e.getMessage(), e);
            // 字段搜索失败返回空列表
            return Collections.emptyList();
        }
    }

    private List<Long> findRelatedTables(List<TableSearchResult> tableResults, Long dsId) {
        // 通过外键关系查找关联表
        Set<Long> relatedTableIds = new HashSet<>();
        Set<Long> mainTableIds = tableResults.stream()
            .map(r -> r.getTable().getTableId())
            .collect(Collectors.toSet());

        for (TableSearchResult result : tableResults) {
            Long tableId = result.getTable().getTableId();
            // 从 Neo4j 获取关联表
            List<TableNode> relatedTables = tableNodeRepository.findRelatedTables(tableId);
            for (TableNode related : relatedTables) {
                // 只添加同一数据源且不在主表列表中的表
                if (related.getDsId().equals(dsId) && !mainTableIds.contains(related.getTableId())) {
                    relatedTableIds.add(related.getTableId());
                }
            }
        }

        return new ArrayList<>(relatedTableIds);
    }

    private String extractQueryFromSql(String sql) {
        // 从SQL中提取表名和关键字段作为查询文本
        StringBuilder sb = new StringBuilder();

        // 提取 FROM 和 JOIN 子句中的表名
        java.util.regex.Pattern tablePattern = java.util.regex.Pattern.compile(
            "(?i)(?:FROM|JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_]*)"
        );
        java.util.regex.Matcher tableMatcher = tablePattern.matcher(sql);
        while (tableMatcher.find()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("表").append(tableMatcher.group(1));
        }

        // 提取 WHERE 子句中的条件字段
        java.util.regex.Pattern wherePattern = java.util.regex.Pattern.compile(
            "(?i)WHERE\\s+(.+?)(?:ORDER|GROUP|HAVING|LIMIT|$)"
        );
        java.util.regex.Matcher whereMatcher = wherePattern.matcher(sql);
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1);
            java.util.regex.Pattern fieldPattern = java.util.regex.Pattern.compile(
                "([a-zA-Z_][a-zA-Z0-9_]*)\\s*[=><]"
            );
            java.util.regex.Matcher fieldMatcher = fieldPattern.matcher(whereClause);
            while (fieldMatcher.find()) {
                sb.append(" 条件").append(fieldMatcher.group(1));
            }
        }

        return sb.length() > 0 ? sb.toString() : sql;
    }
}
