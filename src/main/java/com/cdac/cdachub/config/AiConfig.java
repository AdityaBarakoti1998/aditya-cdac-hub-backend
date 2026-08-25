//package com.cdac.cdachub.config;
//
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.embedding.EmbeddingModel;
//import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
//import org.springframework.ai.vectorstore.SimpleVectorStore;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class AiConfig {
//
//    // Grabs the key directly from your .env file injected by Docker
//    @Value("${GEMINI_API_KEY}")
//    private String geminiApiKey;
//
//    // 1. Required for your Chat feature to work
//    @Bean
//    public ChatClient chatClient(ChatClient.Builder builder) {
//        return builder.build();
//    }
//
//    // 2. THE BYPASS: This intercepts the bugged Spring code
//    @Bean
//    public GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails() {
//        return GoogleGenAiEmbeddingConnectionDetails.builder()
//                .apiKey(geminiApiKey)
//                .projectId("dummy-bypass-id") // This tricks the framework into passing the strict check!
//                .build();
//    }
//
//    // 3. The Vector Database
//  
//}


package com.cdac.cdachub.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class AiConfig {

    @Value("${GEMINI_API_KEY}")
    private String geminiApiKey;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails() {
        return GoogleGenAiEmbeddingConnectionDetails.builder()
                .apiKey(geminiApiKey)
                .projectId("dummy-bypass-id") 
                .build();
    }

    // THE FIX: Native File-Backed Persistent Vector Store
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        
        // When the server starts, check the hard drive volume for saved data
        File persistentFile = new File("/app/vectorstore/store.json");
        if (persistentFile.exists()) {
            store.load(persistentFile);
        }
        
        return store;
    }
}