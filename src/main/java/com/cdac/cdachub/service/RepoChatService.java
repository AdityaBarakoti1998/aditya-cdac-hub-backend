package com.cdac.cdachub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public String askAboutProject(Long projectId, String question) {
        try {
            log.info("Starting chat request for project {} with question: {}", projectId, question);
            
            VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                    .vectorStore(vectorStore)
                    .similarityThreshold(0.5)
                    .topK(5)
                    .filterExpression(new FilterExpressionBuilder().eq("projectId", projectId.toString()).build())
                    .build();

            RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(retriever)
                    .build();

            String response = chatClient.prompt()
                    // THE FIX: Give the AI a personality and rules for general questions
            		.system("You are an expert software engineer and mentor analyzing a developer's project repository. " +
                            "You have two distinct roles: " +
                            "1. FOR PROJECT SPECIFICS: When asked about the files, logic, or structure of THIS specific repository, you MUST rely entirely on the provided context. If the code or file is not in the context, do not guess or invent code. Say: 'I cannot find that specific code in the currently indexed files.' " +
                            "2. FOR GENERAL KNOWLEDGE: If the user says hello, asks for general coding advice, or asks to explain general concepts (e.g., 'What is a GET request?', 'Explain React hooks'), use your extensive general knowledge to answer perfectly. " +
                            "Always be encouraging, highly technical, and concise.")
                    .user(question)
                    .advisors(ragAdvisor)
                    .call()
                    .content();
            
            
                    
            log.info("Successfully generated response for project {}", projectId);
            return response;
            
        } catch (Exception e) {
            // THIS IS THE MAGIC LINE: It forces the hidden error into the terminal!
            log.error("CRITICAL CHAT ERROR for project {}: ", projectId, e);
            throw e; 
        }
    }
}