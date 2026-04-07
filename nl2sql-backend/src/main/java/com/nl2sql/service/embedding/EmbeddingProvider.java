package com.nl2sql.service.embedding;

/**
 * Embedding 服务提供者接口
 * 用于将文本转换为向量表示
 */
public interface EmbeddingProvider {

    /**
     * 获取提供商名称
     */
    String getName();

    /**
     * 生成文本的向量表示
     *
     * @param text 输入文本
     * @return 向量数组
     */
    float[] embed(String text);

    /**
     * 批量生成文本的向量表示
     *
     * @param texts 输入文本数组
     * @return 向量数组的数组
     */
    float[][] embedBatch(String[] texts);

    /**
     * 获取向量维度
     */
    int getDimension();
}
