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
import com.nl2sql.service.llm.DeepSeekProvider;
import com.nl2sql.service.llm.DynamicLLMProvider;
import com.nl2sql.service.llm.LLMProvider;
import com.nl2sql.service.llm.QwenProvider;
import com.nl2sql.service.nl2sql.NL2SqlService;
import com.nl2sql.service.nl2sql.SchemaContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
public class NL2SqlServiceImpl implements NL2SqlService {

    @Autowired
    private DataSourceService dataSourceService;

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

    @Value("${nl2sql.query.sql-retry-count:2}")
    private int sqlRetryCount;

    @Value("${nl2sql.schema.refine-rounds:2}")
    private int schemaRefineRounds;

    @Value("${nl2sql.schema.refine-max-keywords:8}")
    private int schemaRefineMaxKeywords;

    @Value("${nl2sql.schema.max-context-chars:12000}")
    private int maxSchemaContextChars;

    @Autowired
    private SchemaContextService schemaContextService;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Autowired
    private SqlExecutionService sqlExecutionService;

    @Override
    public QueryResponse query(QueryRequest request, Long userId) {
        long startTime = System.currentTimeMillis();
        Conversation conversation = null;

        try {
            conversation = getOrCreateConversation(request, userId);
            long retrieveStart = System.currentTimeMillis();

            // 使用向量检索构建精简 Schema
            String baseSchemaContext = schemaContextService.buildCompactSchemaContext(
                    request.getQuestion(), request.getDsId());
            LLMProvider provider = getLLMProvider();
            String schemaContext = refineSchemaContext(
                    provider,
                    request.getQuestion(),
                    request.getDsId(),
                    baseSchemaContext,
                    null,
                    null);
            log.info("Schema构建完成: baseChars={}, refinedChars={}, costMs={}",
                    safeLength(baseSchemaContext), safeLength(schemaContext),
                    System.currentTimeMillis() - retrieveStart);

            DataSource ds = dataSourceService.getById(request.getDsId());
            String dbType = resolveDbTypeLabel(ds.getType());
            String prompt = promptTemplateService.buildText2SqlPrompt(
                    request.getQuestion(), schemaContext, dbType);

            long llmStart = System.currentTimeMillis();
            String llmResponse = provider.generateSql(prompt);
            String[] parsed = parseLlmResponse(llmResponse);
            String sql = parsed[0];
            String explanation = parsed[1];
            log.info("生成SQL完成: model={}, sqlChars={}, llmCostMs={}",
                    provider.getName(), safeLength(sql), System.currentTimeMillis() - llmStart);
            sqlExecutionService.validateSql(sql);

            // 尝试执行SQL，失败时自动重试（让AI修正SQL）
            List<Map<String, Object>> data = null;
            int retryAttempt = 0;
            String lastError = null;

            while (retryAttempt <= sqlRetryCount) {
                try {
                    long executeStart = System.currentTimeMillis();
                    data = sqlExecutionService.executeSelect(ds, sql);
                    log.info("SQL执行成功: rows={}, executeCostMs={}", data.size(),
                            System.currentTimeMillis() - executeStart);
                    break; // 执行成功，跳出循环
                } catch (Exception execEx) {
                    lastError = execEx.getMessage();
                    retryAttempt++;

                    if (retryAttempt > sqlRetryCount) {
                        // 超过最大重试次数，抛出异常
                        throw new RuntimeException("SQL执行失败（已重试" + sqlRetryCount + "次）: " + lastError);
                    }

                    log.warn("SQL执行失败（第{}次），正在请求AI修正: {}", retryAttempt, lastError);

                    schemaContext = refineSchemaContext(
                            provider,
                            request.getQuestion(),
                            request.getDsId(),
                            schemaContext,
                            sql,
                            lastError);

                    // 构建修正prompt并重新请求AI
                    long fixLlmStart = System.currentTimeMillis();
                    String fixPrompt = promptTemplateService.buildSqlFixPrompt(
                            request.getQuestion(), schemaContext, sql, lastError, dbType);
                    String fixResponse = provider.generateSql(fixPrompt);
                    String[] fixParsed = parseLlmResponse(fixResponse);
                    sql = fixParsed[0];
                    explanation = fixParsed[1];
                    log.info("AI修正SQL完成: retry={}, sqlChars={}, llmCostMs={}",
                            retryAttempt, safeLength(sql), System.currentTimeMillis() - fixLlmStart);
                    sqlExecutionService.validateSql(sql);
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

    private String refineSchemaContext(LLMProvider provider, String question, Long dsId,
            String initialSchemaContext, String currentSql, String errorMessage) {
        String schemaContext = initialSchemaContext == null ? "" : initialSchemaContext;

        for (int i = 0; i < schemaRefineRounds; i++) {
            SchemaRefineDecision decision = askSchemaRefineDecision(
                    provider, question, schemaContext, currentSql, errorMessage);
            if (!decision.needMoreSchema || decision.keywords == null || decision.keywords.isBlank()) {
                break;
            }

            String expandQuery = question + " " + decision.keywords;
            String extraSchema = schemaContextService.buildCompactSchemaContext(expandQuery, dsId);
            String merged = mergeSchemaContexts(schemaContext, extraSchema);
            if (merged.equals(schemaContext)) {
                break;
            }

            log.info("Schema扩展第{}轮完成，关键词: {}", i + 1, decision.keywords);
            schemaContext = merged;
        }

        return schemaContext;
    }

    private SchemaRefineDecision askSchemaRefineDecision(LLMProvider provider, String question,
            String schemaContext, String currentSql, String errorMessage) {
        String prompt = promptTemplateService.buildSchemaRefinePrompt(
                question, schemaContext, currentSql, errorMessage);
        String response;
        try {
            response = provider.generateSql(prompt);
        } catch (Exception e) {
            log.warn("Schema规划请求失败，跳过扩展: {}", e.getMessage());
            return SchemaRefineDecision.noNeed();
        }
        return parseSchemaRefineResponse(response);
    }

    private SchemaRefineDecision parseSchemaRefineResponse(String response) {
        if (response == null || response.isBlank()) {
            return SchemaRefineDecision.noNeed();
        }
        try {
            SchemaRefineDecisionPayload payload = objectMapper.readValue(response, SchemaRefineDecisionPayload.class);
            boolean needMore = payload.shouldNeedMoreSchema();
            String normalizedKeywords = normalizeRefineKeywords(payload.keywords());
            if (!needMore || normalizedKeywords.isBlank()) {
                return SchemaRefineDecision.noNeed();
            }
            return new SchemaRefineDecision(true, normalizedKeywords);
        } catch (Exception e) {
            log.warn("解析Schema规划JSON失败，跳过扩展: {}", e.getMessage());
            return SchemaRefineDecision.noNeed();
        }
    }

    private String mergeSchemaContexts(String base, String extra) {
        if (extra == null || extra.isBlank()) {
            return base == null ? "" : base;
        }
        if (base == null || base.isBlank()) {
            return truncateSchema(extra);
        }

        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (String line : base.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        for (String line : extra.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        String merged = String.join("\n", lines);
        return truncateSchema(merged);
    }

    private String truncateSchema(String schema) {
        if (schema == null) {
            return "";
        }
        if (schema.length() <= maxSchemaContextChars) {
            return schema;
        }
        return schema.substring(0, maxSchemaContextChars);
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

    private record SchemaRefineDecision(boolean needMoreSchema, String keywords) {
        private static SchemaRefineDecision noNeed() {
            return new SchemaRefineDecision(false, "");
        }
    }

    private record SchemaRefineDecisionPayload(Boolean needMoreSchema, String keywords) {
        private boolean shouldNeedMoreSchema() {
            return Boolean.TRUE.equals(needMoreSchema);
        }
    }

    private String normalizeRefineKeywords(String keywordsRaw) {
        if (keywordsRaw == null || keywordsRaw.isBlank()) {
            return "";
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String token : keywordsRaw.trim().split("\\s+")) {
            String cleaned = token.trim();
            if (!cleaned.isBlank()) {
                unique.add(cleaned);
            }
        }
        List<String> limited = new ArrayList<>(unique);
        if (limited.size() > schemaRefineMaxKeywords) {
            limited = limited.subList(0, schemaRefineMaxKeywords);
        }
        return String.join(" ", limited);
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }
}
