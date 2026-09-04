
package com.cdac.cdachub.controller;

import com.cdac.cdachub.service.RepoChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequiredArgsConstructor
public class RepoChatController {

    private final RepoChatService repoChatService;

    @PostMapping("/api/projects/{id}/chat")
    public ResponseEntity<?> chat(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question is required"));
        }
        return ResponseEntity.ok(Map.of("answer", repoChatService.askAboutProject(id, question)));
    }
}