package com.cdac.cdachub.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.cdac.cdachub.model.Project;
import com.cdac.cdachub.repository.ProjectRepository;
import com.cdac.cdachub.service.ProjectService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminProjectController {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;

    // Admin — see every project, any status
    @GetMapping("/api/admin/projects")
    public ResponseEntity<List<Project>> getAllForAdmin() {
        return ResponseEntity.ok(projectRepository.findAll());
    }

    // Admin — permanently delete a project (cascades to its files + team members)
    @DeleteMapping("/api/admin/projects/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable Long id) throws IOException {
        if (!projectRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        // Ye apne ko Ghoste failes se bachata hai. Agar aap sirf database se delete karenge, to aapke "uploads/" folder me files reh jaayengi aur space le rahi hongi.
        // WHAT IT DOES: Instead of just deleting the row from the database (which leaves ghost files taking up space), 
        // it hands the job over to the ProjectService. The service will now go into your "uploads/" folder, 
        // delete the actual PDF/ZIP files off the hard drive, and THEN delete the database record.
        // 
        // WHY THE TRY-CATCH IS GONE: You no longer need the try-catch block here because your new 
        // GlobalExceptionHandler will automatically catch any errors and send a clean JSON response if this fails!
        projectService.deleteProject(id);
        
        return ResponseEntity.ok(Map.of("message", "Project deleted"));
    }
}