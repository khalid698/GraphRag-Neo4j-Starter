package com.khalid698.tutorials.codegraph.config;

import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

@Configuration
public class LangChainConfig {

    @Bean
    public EmbeddingModel embeddingModel(@Value("${openai.api-key}") String apiKey,
                                         @Value("${app.embedding-model:text-embedding-3-small}") String modelName) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    @Bean
    public OpenAiChatModel chatLanguageModel(@Value("${openai.api-key}") String apiKey,
                                               @Value("${app.chat-model:gpt-4o-mini}") String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    @Bean
    public Neo4jEmbeddingStore chunkEmbeddingStore(Driver driver,
                                                   EmbeddingModel embeddingModel,
                                                   @Value("${app.vector-dimensions:1536}") int vectorDimensions) {
        int dims = embeddingModel != null ? embeddingModel.dimension() : vectorDimensions;
        return Neo4jEmbeddingStore.builder()
                .driver(driver)
                .label("ChunkEmbeddingStore")
                .idProperty("id")
                .textProperty("text")
                .embeddingProperty("embedding")
                .indexName("chunk_embedding_store_idx")
                .dimension(dims)
                .build();
    }
}
