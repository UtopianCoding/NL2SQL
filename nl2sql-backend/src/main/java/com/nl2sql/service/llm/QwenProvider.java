package com.nl2sql.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class QwenProvider implements LLMProvider {

    @Value("${nl2sql.llm.qwen.api-key}")
    private String apiKey;

    @Value("${nl2sql.llm.qwen.base-url}")
    private String baseUrl;

    @Value("${nl2sql.llm.qwen.model}")
    private String model;

    @Value("${nl2sql.llm.qwen.max-tokens}")
    private int maxTokens;

    @Value("${nl2sql.llm.qwen.temperature}")
    private double temperature;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client;

    public QwenProvider() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return "qwen";
    }

    @Override
    public String generateSql(String prompt) {
        try {
            String requestBody = objectMapper.writeValueAsString(new ChatRequest(
                    model,
                    new Message[]{new Message("user", prompt)},
                    maxTokens,
                    temperature
            ));

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("API调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                String content = root.path("choices").get(0).path("message").path("content").asText();
                
                return cleanSql(content);
            }
        } catch (IOException e) {
            log.error("Qwen API调用失败: {}", e.getMessage());
            throw new RuntimeException("SQL生成失败: " + e.getMessage());
        }
    }

    @Override
    public float[] generateEmbedding(String text) {
        // 通义千问embedding实现
        return new float[0];
    }

    private String cleanSql(String content) {
        String sql = content.trim();
        if (sql.startsWith("```sql")) {
            sql = sql.substring(6);
        } else if (sql.startsWith("```")) {
            sql = sql.substring(3);
        }
        if (sql.endsWith("```")) {
            sql = sql.substring(0, sql.length() - 3);
        }
        return sql.trim();
    }

    private record ChatRequest(String model, Message[] messages, int max_tokens, double temperature) {}
    private record Message(String role, String content) {}
}
