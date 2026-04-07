package com.nl2sql.service.nl2sql.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.mapper.ChatMessageMapper;
import com.nl2sql.mapper.ConversationMapper;
import com.nl2sql.mapper.QueryHistoryMapper;
import com.nl2sql.model.dto.QueryRequest;
import com.nl2sql.model.dto.QueryResponse;
import com.nl2sql.model.entity.ChatMessage;
import com.nl2sql.model.entity.Conversation;
import com.nl2sql.model.entity.DataSource;
import com.nl2sql.model.entity.QueryHistory;
import com.nl2sql.model.entity.AiModelConfig;
import com.nl2sql.service.aimodel.AiModelConfigService;
import com.nl2sql.service.datasource.DataSourceService;
import com.nl2sql.service.graph.Neo4jService;
import com.nl2sql.service.llm.DeepSeekProvider;
import com.nl2sql.service.llm.DynamicLLMProvider;
import com.nl2sql.service.llm.LLMProvider;
import com.nl2sql.service.llm.QwenProvider;
import com.nl2sql.service.nl2sql.NL2SqlService;
import com.nl2sql.service.nl2sql.SchemaContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

@Slf4j
@Service
public class NL2SqlServiceImpl implements NL2SqlService {

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private Neo4jService neo4jService;

    @Autowired
    private QueryHistoryMapper queryHistoryMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeepSeekProvider deepSeekProvider;

    @Autowired
    private QwenProvider qwenProvider;

    @Autowired
    private AiModelConfigService aiModelConfigService;

    @Value("${nl2sql.llm.provider}")
    private String llmProvider;

    @Value("${nl2sql.query.max-result-rows}")
    private int maxResultRows;

    @Value("${nl2sql.query.timeout-seconds}")
    private int queryTimeout;

    @Value("${nl2sql.query.sql-retry-count:2}")
    private int sqlRetryCount;

    private String promptTemplate;
    private String fixPromptTemplate;

    @Autowired
    private SchemaContextService schemaContextService;

    @Override
    public QueryResponse query(QueryRequest request, Long userId) {
        long startTime = System.currentTimeMillis();
        Conversation conversation = null;

        try {
            conversation = getOrCreateConversation(request, userId);

            // 使用向量检索构建精简 Schema
            String schemaContext = schemaContextService.buildCompactSchemaContext(
                    request.getQuestion(), request.getDsId());
            DataSource ds = dataSourceService.getById(request.getDsId());
            String dbType = resolveDbTypeLabel(ds.getType());
            String prompt = buildPrompt(request.getQuestion(), schemaContext, conversation, dbType);

            LLMProvider provider = getLLMProvider();
            String llmResponse = provider.generateSql(prompt);
            String[] parsed = parseLlmResponse(llmResponse);
            String sql = parsed[0];
            String explanation = parsed[1];
            log.info("生成SQL: {}", sql);
            validateSql(sql);

            // 尝试执行SQL，失败时自动重试（让AI修正SQL）
            List<Map<String, Object>> data = null;
            int retryAttempt = 0;
            String lastError = null;

            while (retryAttempt <= sqlRetryCount) {
                try {
                    data = executeSql(ds, sql);
                    break; // 执行成功，跳出循环
                } catch (Exception execEx) {
                    lastError = execEx.getMessage();
                    retryAttempt++;

                    if (retryAttempt > sqlRetryCount) {
                        // 超过最大重试次数，抛出异常
                        throw new RuntimeException("SQL执行失败（已重试" + sqlRetryCount + "次）: " + lastError);
                    }

                    log.warn("SQL执行失败（第{}次），正在请求AI修正: {}", retryAttempt, lastError);

                    // 构建修正prompt并重新请求AI
                    String fixPrompt = buildFixPrompt(request.getQuestion(), schemaContext, sql, lastError, dbType);
                    String fixResponse = provider.generateSql(fixPrompt);
                    String[] fixParsed = parseLlmResponse(fixResponse);
                    sql = fixParsed[0];
                    explanation = fixParsed[1];
                    log.info("AI修正后的SQL（第{}次重试）: {}", retryAttempt, sql);
                    validateSql(sql);
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;

            QueryHistory history = saveHistory(userId, request, sql, "SUCCESS",
                    data.size(), (int) executionTime, null, provider.getName());

            updateConversation(conversation, request.getQuestion(), sql);

            // 保存用户消息到chat_message
            saveChatMessage(conversation.getId(), userId, "user", request.getQuestion(),
                    null, null, null, null, null, null);

            // 构建explanation附加重试信息
            String finalExplanation = explanation;
            if (retryAttempt > 0) {
                finalExplanation = (explanation != null ? explanation : "") +
                        "\n\n（注：初始SQL执行出错，经过" + retryAttempt + "次AI自动修正后成功执行）";
            }

            // 保存助手回复到chat_message
            saveChatMessage(conversation.getId(), userId, "assistant", finalExplanation,
                    sql, data, data.size(), (int) executionTime, null, history.getId());

            return QueryResponse.builder()
                    .historyId(history.getId())
                    .conversationId(conversation.getId())
                    .question(request.getQuestion())
                    .sql(sql)
                    .explanation(finalExplanation)
                    .data(data)
                    .totalRows(data.size())
                    .executionTimeMs(executionTime)
                    .fromCache(false)
                    .status("SUCCESS")
                    .build();

        } catch (Exception e) {
            log.error("查询执行失败: {}", e.getMessage());
            long executionTime = System.currentTimeMillis() - startTime;

            saveHistory(userId, request, null, "FAILED", 0,
                    (int) executionTime, e.getMessage(), llmProvider);

            // 保存失败时的对话记录
            Long convId = conversation != null ? conversation.getId() : request.getConversationId();
            if (convId != null) {
                if (conversation != null && (conversation.getTitle() == null || conversation.getTitle().isEmpty())) {
                    String title = request.getQuestion().length() > 50
                            ? request.getQuestion().substring(0, 50) + "..."
                            : request.getQuestion();
                    conversation.setTitle(title);
                    conversationMapper.updateById(conversation);
                }

                saveChatMessage(convId, userId, "user", request.getQuestion(),
                        null, null, null, null, null, null);
                saveChatMessage(convId, userId, "assistant", "查询失败",
                        null, null, null, (int) executionTime, e.getMessage(), null);
            }

            return QueryResponse.builder()
                    .conversationId(convId)
                    .question(request.getQuestion())
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }

    private Conversation getOrCreateConversation(QueryRequest request, Long userId) {
        if (request.getConversationId() != null) {
            Conversation conversation = conversationMapper.selectById(request.getConversationId());
            if (conversation != null) {
                return conversation;
            }
        }
        return createNewConversation(userId, request.getDsId());
    }

    private Conversation createNewConversation(Long userId, Long dsId) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setDsId(dsId);
        conversation.setStatus(1);
        conversation.setTurnCount(0);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private void updateConversation(Conversation conversation, String question, String sql) {
        if (conversation.getTitle() == null || conversation.getTitle().isEmpty()) {
            String title = question.length() > 50 ? question.substring(0, 50) + "..." : question;
            conversation.setTitle(title);
        }
        conversation.setTurnCount(conversation.getTurnCount() + 1);

        String context = conversation.getContextData();
        if (context == null) context = "[]";

        conversationMapper.updateById(conversation);
    }

    private String buildPrompt(String question, String schemaContext, Conversation conversation, String dbType) {
        if (promptTemplate == null) {
            try {
                ClassPathResource resource = new ClassPathResource("prompts/text2sql.txt");
                promptTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("加载prompt模板失败", e);
                promptTemplate = "根据以下数据库结构，将用户问题转换为SQL：\n{{schema_context}}\n\n问题：{{user_question}}";
            }
        }

        return promptTemplate
                .replace("{{db_type}}", dbType)
                .replace("{{schema_context}}", schemaContext)
                .replace("{{user_question}}", question)
                .replace("{{similar_queries}}", "")
                .replace("{{conversation_history}}", "");
    }

    private String resolveDbTypeLabel(String type) {
        if (type == null) return "MySQL";
        return switch (type.toLowerCase()) {
            case "mysql" -> "MySQL";
            case "postgresql", "pg" -> "PostgreSQL";
            case "oracle" -> "Oracle";
            case "sqlserver", "mssql" -> "SQL Server (MSSQL)";
            default -> type;
        };
    }

    private String buildFixPrompt(String question, String schemaContext, String failedSql, String errorMessage, String dbType) {
        if (fixPromptTemplate == null) {
            try {
                ClassPathResource resource = new ClassPathResource("prompts/sql_fix.txt");
                fixPromptTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("加载SQL修正prompt模板失败", e);
                fixPromptTemplate = "之前生成的SQL执行失败，请修正。\n\n数据库结构：\n{{schema_context}}\n\n用户问题：{{user_question}}\n\n失败SQL：{{failed_sql}}\n\n错误信息：{{error_message}}";
            }
        }

        return fixPromptTemplate
                .replace("{{db_type}}", dbType)
                .replace("{{schema_context}}", schemaContext)
                .replace("{{user_question}}", question)
                .replace("{{failed_sql}}", failedSql)
                .replace("{{error_message}}", errorMessage);
    }

    private LLMProvider getLLMProvider() {
        // 优先从数据库获取默认模型配置
        try {
            AiModelConfig defaultConfig = aiModelConfigService.getDefault();
            if (defaultConfig != null) {
                Map<String, Object> extraParams = Map.of();
                if (defaultConfig.getParams() != null && !defaultConfig.getParams().isEmpty()) {
                    try {
                        extraParams = objectMapper.readValue(defaultConfig.getParams(),
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        log.warn("解析模型参数失败: {}", e.getMessage());
                    }
                }
                log.info("使用数据库默认模型: {} ({})", defaultConfig.getModelName(), defaultConfig.getBaseModel());
                return new DynamicLLMProvider(
                        defaultConfig.getModelName(),
                        defaultConfig.getApiUrl(),
                        defaultConfig.getApiKey(),
                        defaultConfig.getBaseModel(),
                        extraParams
                );
            }
        } catch (Exception e) {
            log.warn("获取默认模型配置失败，回退到配置文件: {}", e.getMessage());
        }

        // 回退到配置文件中的静态提供者
        return switch (llmProvider.toLowerCase()) {
            case "qwen" -> qwenProvider;
            default -> deepSeekProvider;
        };
    }

    private void validateSql(String sql) {
        String upperSql = sql.toUpperCase().trim();

        if (!upperSql.startsWith("SELECT")) {
            throw new RuntimeException("只允许执行SELECT查询");
        }

        List<String> dangerousKeywords = Arrays.asList(
                "DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE",
                "ALTER", "CREATE", "GRANT", "REVOKE", "EXEC", "EXECUTE"
        );

        for (String keyword : dangerousKeywords) {
            // 使用词边界匹配，避免误判如 deleted 字段名
            String pattern = "(?<![A-Z_])" + keyword + "(?![A-Z_])";
            if (java.util.regex.Pattern.compile(pattern).matcher(upperSql).find()) {
                throw new RuntimeException("SQL包含不允许的关键词: " + keyword);
            }
        }
    }

    private List<Map<String, Object>> executeSql(DataSource ds, String sql) {
        String jdbcUrl = buildJdbcUrl(ds);
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, ds.getUsername(), ds.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.setQueryTimeout(queryTimeout);
            stmt.setMaxRows(maxResultRows);

            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("SQL执行失败: {}", e.getMessage());
            throw new RuntimeException("SQL执行失败: " + e.getMessage());
        }

        return results;
    }

    private String buildJdbcUrl(DataSource ds) {
        return switch (ds.getType().toLowerCase()) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
            case "postgresql", "pg" -> String.format("jdbc:postgresql://%s:%d/%s",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
            case "oracle" -> String.format("jdbc:oracle:thin:@%s:%d:%s",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
            case "sqlserver", "mssql" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
            case "oceanbase" -> String.format("jdbc:mysql://%s:%d/%s",  ds.getHost(), ds.getPort(), ds.getDatabaseName());

            default -> throw new RuntimeException("不支持的数据库类型: " + ds.getType());
        };
    }

    private QueryHistory saveHistory(Long userId, QueryRequest request, String sql,
            String status, int resultRows, int executionTime, String errorMessage, String llmModel) {
        QueryHistory history = new QueryHistory();
        history.setUserId(userId);
        history.setDsId(request.getDsId());
        history.setConversationId(request.getConversationId());
        history.setNaturalQuery(request.getQuestion());
        history.setGeneratedSql(sql);
        history.setExecutionStatus(status);
        history.setResultRows(resultRows);
        history.setExecutionTimeMs(executionTime);
        history.setErrorMessage(errorMessage);
        history.setLlmModel(llmModel);
        history.setIsFromCache(0);
        history.setIsFavorite(0);

        queryHistoryMapper.insert(history);
        return history;
    }

    private void saveChatMessage(Long conversationId, Long userId, String role, String content,
            String sql, List<Map<String, Object>> data, Integer resultRows,
            Integer executionTimeMs, String errorMessage, Long historyId) {
        ChatMessage msg = new ChatMessage();
        msg.setConversationId(conversationId);
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setSqlText(sql);
        if (data != null && !data.isEmpty()) {
            try {
                msg.setResultData(objectMapper.writeValueAsString(data));
            } catch (Exception e) {
                log.warn("序列化查询结果失败", e);
            }
        }
        msg.setResultRows(resultRows);
        msg.setExecutionTimeMs(executionTimeMs);
        msg.setErrorMessage(errorMessage);
        msg.setHistoryId(historyId);
        chatMessageMapper.insert(msg);
    }

    private String[] parseLlmResponse(String response) {
        String sql;
        String explanation = null;
        if (response.contains("---SQL---") && response.contains("---EXPLANATION---")) {
            int sqlStart = response.indexOf("---SQL---") + "---SQL---".length();
            int explStart = response.indexOf("---EXPLANATION---");
            sql = response.substring(sqlStart, explStart).trim();
            explanation = response.substring(explStart + "---EXPLANATION---".length()).trim();
        } else {
            sql = response.trim();
        }
        // 清理SQL中可能残留的markdown标记
        if (sql.startsWith("```sql")) {
            sql = sql.substring(6);
        } else if (sql.startsWith("```")) {
            sql = sql.substring(3);
        }
        if (sql.endsWith("```")) {
            sql = sql.substring(0, sql.length() - 3);
        }
        return new String[]{sql.trim(), explanation};
    }
}
