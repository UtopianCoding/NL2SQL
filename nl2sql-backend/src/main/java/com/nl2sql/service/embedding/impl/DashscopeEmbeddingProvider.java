package com.nl2sql.service.embedding.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nl2sql.service.embedding.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 阿里 Dashscope Embedding 服务实现
 * 使用通义千问的 embedding 能力
 */
@Slf4j
@Service
public class DashscopeEmbeddingProvider implements EmbeddingProvider {

    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";

    @Value("${nl2sql.llm.qwen.api-key}")
    private String apiKey;

    @Value("${milvus.embedding.dimension:768}")
    private int dimension;

    private final OkHttpClient client;

    public DashscopeEmbeddingProvider() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return "dashscope";
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Empty text provided for embedding, returning zero vector");
            return new float[dimension];
        }

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "text-embedding-v2");
            requestBody.put("input", text);

            RequestBody body = RequestBody.create(
                    requestBody.toJSONString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("Embedding API调用失败: code={}, body={}", response.code(), errorBody);
                    throw new RuntimeException("Embedding API调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                JSONObject jsonResponse = JSON.parseObject(responseBody);
                JSONObject output = jsonResponse.getJSONObject("output");
                JSONArray embeddings = output.getJSONArray("embeddings");

                if (embeddings == null || embeddings.isEmpty()) {
                    throw new RuntimeException("API返回的embedding为空");
                }

                JSONObject embeddingObj = embeddings.getJSONObject(0);
                JSONArray embeddingArray = embeddingObj.getJSONArray("embedding");

                float[] result = new float[embeddingArray.size()];
                for (int i = 0; i < embeddingArray.size(); i++) {
                    result[i] = embeddingArray.getFloatValue(i);
                }

                log.debug("Successfully generated embedding for text, dimension: {}", result.length);
                return result;
            }
        } catch (IOException e) {
            log.error("Embedding API调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding生成失败: " + e.getMessage(), e);
        }
    }

    @Override
    public float[][] embedBatch(String[] texts) {
        if (texts == null || texts.length == 0) {
            return new float[0][];
        }

        log.info("开始批量生成embedding，文本数量: {}", texts.length);
        float[][] results = new float[texts.length][];

        // 由于API限制，采用串行方式处理
        for (int i = 0; i < texts.length; i++) {
            try {
                results[i] = embed(texts[i]);
                log.debug("已完成 {}/{} 个embedding生成", i + 1, texts.length);
            } catch (Exception e) {
                log.error("生成第{}个embedding失败: {}", i, e.getMessage());
                results[i] = new float[dimension]; // 使用零向量作为回退
            }
        }

        log.info("批量embedding生成完成，成功: {}/{}", 
            Arrays.stream(results).filter(r -> r != null && Arrays.stream(r).anyMatch(v -> v != 0)).count(), 
            texts.length);
        return results;
    }

    @Override
    public int getDimension() {
        return dimension;
    }
}
