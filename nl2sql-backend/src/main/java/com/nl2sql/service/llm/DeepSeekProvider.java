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
public class DeepSeekProvider implements LLMProvider {

    @Value("${nl2sql.llm.deepseek.api-key}")
    private String apiKey;

    @Value("${nl2sql.llm.deepseek.base-url}")
    private String baseUrl;

    @Value("${nl2sql.llm.deepseek.model}")
    private String model;

    @Value("${nl2sql.llm.deepseek.max-tokens}")
    private int maxTokens;

    @Value("${nl2sql.llm.deepseek.temperature}")
    private double temperature;

    @Value("${nl2sql.llm.deepseek.timeout}")
    private int timeout;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client;

    public DeepSeekProvider() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return "deepseek";
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
                
                // 清理SQL（去除可能的markdown标记）
                return cleanSql(content);
            }
        } catch (IOException e) {
            log.error("DeepSeek API调用失败: {}", e.getMessage());
            throw new RuntimeException("SQL生成失败: " + e.getMessage());
        }
    }

    @Override
    public float[] generateEmbedding(String text) {
        // DeepSeek暂不支持embedding，返回空数组
        // 实际项目中可以调用其他embedding服务
        return new float[0];
    }

    private String cleanSql(String content) {
        String sql = content.trim();
        // 去除markdown代码块标记
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

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ChatRequest {
        private String model;
        private Message[] messages;
        private int max_tokens;
        private double temperature;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class Message {
        private String role;
        private String content;
    }
}
