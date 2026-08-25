package com.cdac.cdachub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class RepoFetcherService {

    @Value("${github.token:}")
    private String githubToken;

    // THE FIX: Use a Blacklist instead of a Whitelist to support ALL programming languages
    private static final Set<String> SKIP_EXTENSIONS = Set.of(
        ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".pdf", 
        ".jar", ".class", ".exe", ".dll", ".zip", ".tar", ".gz",
        ".mp4", ".mp3", ".wav", ".ttf", ".woff", ".woff2", ".eot"
    );
    
    private static final Set<String> SKIP_FILES = Set.of(
        "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "pom.xml"
    );

    private static final Set<String> SKIP_DIRS = Set.of(
        "node_modules", ".git", "target", "build", "dist", ".idea", ".vscode"
    );
    
    private static final long MAX_FILE_SIZE = 100_000; // Skip files larger than ~100KB

    public List<RepoFile> fetchRepoFiles(String gitLink) throws IOException, InterruptedException {
        String[] ownerRepo = parseOwnerRepo(gitLink);
        String zipUrl = "https://api.github.com/repos/%s/%s/zipball".formatted(ownerRepo[0], ownerRepo[1]);

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(zipUrl))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CDACHub-Application");

        if (githubToken != null && !githubToken.isBlank()) {
            builder.header("Authorization", "Bearer " + githubToken);
        }

        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() == 301 || response.statusCode() == 302) {
            String redirectUrl = response.headers().firstValue("Location").orElse("");
            HttpRequest redirectReq = HttpRequest.newBuilder(URI.create(redirectUrl))
                    .header("User-Agent", "CDACHub-Application")
                    .build(); 
            response = client.send(redirectReq, HttpResponse.BodyHandlers.ofByteArray());
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("Could not fetch repo (status " + response.statusCode() + "). Is it public?");
        }

        List<RepoFile> files = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getSize() > MAX_FILE_SIZE) continue;
                
                String path = entry.getName();
                String fileName = path.substring(path.lastIndexOf('/') + 1).toLowerCase();

                // Check if the file is inside a skipped directory
                boolean inSkipDir = SKIP_DIRS.stream().anyMatch(dir -> path.contains("/" + dir + "/") || path.startsWith(dir + "/"));
                
                // Check if it has a bad extension or is a specific bad file
                boolean isBadExtension = SKIP_EXTENSIONS.stream().anyMatch(fileName::endsWith);
                boolean isBadFile = SKIP_FILES.contains(fileName);

                // Only add the file if it passes all blacklist checks
                if (inSkipDir || isBadExtension || isBadFile) continue;
                
                files.add(new RepoFile(path, new String(zis.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        return files;
    }

    private String[] parseOwnerRepo(String gitLink) {
        String cleaned = gitLink.replaceAll("\\.git$", "").replaceAll("/$", "");
        String[] parts = cleaned.split("/");
        return new String[]{ parts[parts.length - 2], parts[parts.length - 1] };
    }

    public record RepoFile(String path, String content) {}
}