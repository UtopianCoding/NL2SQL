package com.nl2sql.service.nl2sql.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class PromptTemplateService {

    @Value("${nl2sql.schema.max-context-chars:12000}")
    private int maxSchemaContextChars;

    private String promptTemplate;
    private String fixPromptTemplate;
    private String schemaRefinePromptTemplate;

    public String buildText2SqlPrompt(String question, String schemaContext, String dbType) {
        if (promptTemplate == null) {
            promptTemplate = loadTemplate("prompts/text2sql.txt",
                    "根据以下数据库结构，将用户问题转换为SQL：\n{{schema_context}}\n\n问题：{{user_question}}");
        }
        return promptTemplate
                .replace("{{db_type}}", nullToEmpty(dbType))
                .replace("{{schema_context}}", nullToEmpty(schemaContext))
                .replace("{{user_question}}", nullToEmpty(question))
                .replace("{{similar_queries}}", "")
                .replace("{{conversation_history}}", "");
    }

    public String buildSqlFixPrompt(String question, String schemaContext, String failedSql,
            String errorMessage, String dbType) {
        if (fixPromptTemplate == null) {
            fixPromptTemplate = loadTemplate("prompts/sql_fix.txt",
                    "之前生成的SQL执行失败，请修正。\n\n数据库结构：\n{{schema_context}}\n\n用户问题：{{user_question}}\n\n失败SQL：{{failed_sql}}\n\n错误信息：{{error_message}}");
        }
        return fixPromptTemplate
                .replace("{{db_type}}", nullToEmpty(dbType))
                .replace("{{schema_context}}", nullToEmpty(schemaContext))
                .replace("{{user_question}}", nullToEmpty(question))
                .replace("{{failed_sql}}", nullToEmpty(failedSql))
                .replace("{{error_message}}", nullToEmpty(errorMessage));
    }

    public String buildSchemaRefinePrompt(String question, String schemaContext,
            String currentSql, String errorMessage) {
        if (schemaRefinePromptTemplate == null) {
            schemaRefinePromptTemplate = loadTemplate("prompts/schema_refine.txt",
                    """
你是一个数据库Schema检索规划助手。任务：判断当前Schema是否足够回答用户问题。如果不够，只输出需要补充检索的关键词。不要输出SQL。

## 用户问题
{{user_question}}

## 当前Schema上下文（可能已截断）
{{schema_context}}

## 当前SQL（如有）
{{current_sql}}

## 执行错误（如有）
{{error_message}}

请仅输出严格JSON，不要输出任何其他文本：
{"needMoreSchema": true/false, "keywords": "keyword1 keyword2"}
""");
        }

        String safeSchema = nullToEmpty(schemaContext);
        if (safeSchema.length() > maxSchemaContextChars) {
            safeSchema = safeSchema.substring(0, maxSchemaContextChars);
        }

        return schemaRefinePromptTemplate
                .replace("{{user_question}}", nullToEmpty(question))
                .replace("{{schema_context}}", safeSchema)
                .replace("{{current_sql}}", nullToEmpty(currentSql))
                .replace("{{error_message}}", nullToEmpty(errorMessage));
    }

    private String loadTemplate(String path, String fallback) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("加载模板失败: {}", path, e);
            return fallback;
        }
    }

    private String nullToEmpty(String input) {
        return input == null ? "" : input;
    }
}
