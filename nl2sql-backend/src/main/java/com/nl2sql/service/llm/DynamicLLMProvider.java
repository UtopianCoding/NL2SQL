package com.nl2sql.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 动态LLM提供者，根据数据库中的AI模型配置调用不同的LLM API。
 * 所有支持OpenAI兼容接口的模型都可以通过此提供者调用。
 */
@Slf4j
public class DynamicLLMProvider implements LLMProvider {

    private final String name;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final Map<String, Object> extraParams;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client;

    public DynamicLLMProvider(String name, String apiUrl, String apiKey, String model, Map<String, Object> extraParams) {
        this.name = name;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.extraParams = extraParams != null ? extraParams : Map.of();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String generateSql(String prompt) {
        try {
            // 构建请求体
            var requestMap = new java.util.LinkedHashMap<String, Object>();
            requestMap.put("model", model);
            requestMap.put("messages", new Object[]{Map.of("role", "user", "content", prompt)});

            // 设置默认的max_tokens和temperature
            int maxTokens = 4000;
            double temperature = 0.1;

            // 从extraParams中提取参数
            if (extraParams.containsKey("max_tokens")) {
                maxTokens = parseIntParam(extraParams.get("max_tokens"), maxTokens);
            }
            if (extraParams.containsKey("temperature")) {
                temperature = parseDoubleParam(extraParams.get("temperature"), temperature);
            }

            requestMap.put("max_tokens", maxTokens);
            requestMap.put("temperature", temperature);

            // 将其他额外参数也加入请求体（如extra_body等）
            for (Map.Entry<String, Object> entry : extraParams.entrySet()) {
                String key = entry.getKey();
                if (!"max_tokens".equals(key) && !"temperature".equals(key)) {
                    requestMap.put(key, entry.getValue());
                }
            }

            String requestBody = objectMapper.writeValueAsString(requestMap);

            // 构建URL：如果apiUrl已经包含/chat/completions则直接使用，否则追加
            String url = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
            if (!url.endsWith("/chat/completions")) {
                url = url + "/chat/completions";
            }

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            log.info("调用LLM [{}] model={}, url={}", name, model, url);

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "";
                    log.error("LLM API调用失败: code={}, body={}", response.code(), errBody);
                    throw new RuntimeException("API调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                String content = root.path("choices").get(0).path("message").path("content").asText();

                return cleanSql(content);
            }
        } catch (IOException e) {
            log.error("LLM [{}] API调用失败: {}", name, e.getMessage());
            throw new RuntimeException("SQL生成失败: " + e.getMessage());
        }
    }

    @Override
    public float[] generateEmbedding(String text) {
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

    private int parseIntParam(Object value, int defaultValue) {
        try {
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double parseDoubleParam(Object value, double defaultValue) {
        try {
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
