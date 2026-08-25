package com.cdac.cdachub.service;

import com.cdac.cdachub.model.Project;
import com.cdac.cdachub.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
// --- ADDED IMPORTS FOR FILE PERSISTENCE ---
import org.springframework.ai.vectorstore.SimpleVectorStore;
import java.io.File;
// ------------------------------------------
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoIndexingService {

    private final RepoFetcherService repoFetcherService;
    private final VectorStore vectorStore;
    private final ProjectRepository projectRepository;

    @Async
    public void indexProject(Long projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found"));

            List<RepoFetcherService.RepoFile> files = repoFetcherService.fetchRepoFiles(project.getGitLink());
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> documents = new ArrayList<>();

            for (RepoFetcherService.RepoFile file : files) {
                Document doc = new Document(file.content(), Map.of(
                        "projectId", projectId.toString(),
                        "filePath", file.path()
                ));
                documents.addAll(splitter.apply(List.of(doc)));
            }

            // We process one chunk at a time and pause for 4.5 seconds to bypass the Free Tier API limits
            log.info("Total chunks generated: {}. Starting rate-limited indexing...", documents.size());
            for (int i = 0; i < documents.size(); i++) {
                vectorStore.add(List.of(documents.get(i)));
                log.info("Indexed chunk {} of {}...", (i + 1), documents.size());
                Thread.sleep(4500); // 4.5 second delay
            }
            
            // --- THE FIX: WRITE TO HARD DRIVE ---
            // After successfully converting everything to vectors, save it to the Docker volume
            if (this.vectorStore instanceof SimpleVectorStore simpleStore) {
                File persistentFile = new File("/app/vectorstore/store.json");
                persistentFile.getParentFile().mkdirs(); // Ensure directory exists
                simpleStore.save(persistentFile);
                log.info("Vector database permanently saved to disk.");
            }
            // ------------------------------------
            
            // Re-fetch the project to get the freshest database version after the long delay
            Project latestProject = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found"));

            latestProject.setIndexed(true);
            latestProject.setIndexedAt(LocalDateTime.now());
            projectRepository.save(latestProject);

            log.info("Indexed {} chunks for project {}", documents.size(), projectId);
        } catch (Exception e) {
            log.error("Failed to index project {}", projectId, e);
        }
    }
}