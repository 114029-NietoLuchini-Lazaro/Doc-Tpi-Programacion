package com.example.demo.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans de LangChain4j construidos de forma programática sobre el endpoint
 * OpenAI-compat de Google Gemini (mismo protocolo y base_url que usaba Spring AI).
 * Se leen variables de entorno con defaults en código para no depender de
 * application.properties ni del starter de Spring Boot.
 */
@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    private final String baseUrl;
    private final String apiKey;

    public LangChain4jConfig(
            @Value("${spring.ai.openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Bean
    public OpenAiChatModel chatLanguageModel() {
        String model = resolveEnv("GEMINI_MODEL", "gemini-flash-lite-latest");
        log.info("LangChain4j ChatLanguageModel configurado sobre '{}' (modelo '{}').", baseUrl, model);
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.3)
                .maxTokens(1500)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        String model = resolveEnv("GEMINI_EMBEDDING_MODEL", "gemini-embedding-2");
        log.info("LangChain4j EmbeddingModel configurado sobre '{}' (modelo '{}').", baseUrl, model);
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .build();
    }

    private static String resolveEnv(String name, String fallback) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}