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
import com.nl2sql.service.datasource.DataSourceService;
import com.nl2sql.service.graph.Neo4jService;
import com.nl2sql.service.llm.DeepSeekProvider;
import com.nl2sql.service.llm.LLMProvider;
import com.nl2sql.service.llm.QwenProvider;
import com.nl2sql.service.nl2sql.NL2SqlService;
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

    @Value("${nl2sql.llm.provider}")
    private String llmProvider;

    @Value("${nl2sql.query.max-result-rows}")
    private int maxResultRows;

    @Value("${nl2sql.query.timeout-seconds}")
    private int queryTimeout;

    private String promptTemplate;

    @Override
    public QueryResponse query(QueryRequest request, Long userId) {
        long startTime = System.currentTimeMillis();

        try {
            Conversation conversation = getOrCreateConversation(request, userId);
            String schemaContext = neo4jService.buildSchemaContext(request.getDsId());
            String prompt = buildPrompt(request.getQuestion(), schemaContext, conversation);

            LLMProvider provider = getLLMProvider();
            String sql = provider.generateSql(prompt);

            validateSql(sql);

            DataSource ds = dataSourceService.getById(request.getDsId());
            List<Map<String, Object>> data = executeSql(ds, sql);

            long executionTime = System.currentTimeMillis() - startTime;

            QueryHistory history = saveHistory(userId, request, sql, "SUCCESS",
                    data.size(), (int) executionTime, null, provider.getName());

            updateConversation(conversation, request.getQuestion(), sql);

            // 保存用户消息到chat_message
            saveChatMessage(conversation.getId(), userId, "user", request.getQuestion(),
                    null, null, null, null, null, null);
            // 保存助手回复到chat_message
            saveChatMessage(conversation.getId(), userId, "assistant", "查询完成",
                    sql, data, data.size(), (int) executionTime, null, history.getId());

            return QueryResponse.builder()
                    .historyId(history.getId())
                    .conversationId(conversation.getId())
                    .question(request.getQuestion())
                    .sql(sql)
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
            Long convId = request.getConversationId();
            if (convId != null) {
                saveChatMessage(convId, userId, "user", request.getQuestion(),
                        null, null, null, null, null, null);
                saveChatMessage(convId, userId, "assistant", "查询失败",
                        null, null, null, (int) executionTime, e.getMessage(), null);
            }

            return QueryResponse.builder()
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

    private String buildPrompt(String question, String schemaContext, Conversation conversation) {
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
                .replace("{{schema_context}}", schemaContext)
                .replace("{{user_question}}", question)
                .replace("{{similar_queries}}", "")
                .replace("{{conversation_history}}", "");
    }

    private LLMProvider getLLMProvider() {
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
}
