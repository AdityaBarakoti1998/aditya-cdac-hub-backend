package com.cdac.cdachub.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map; // ✅ FIX 1: Added Map import

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cdac.cdachub.dto.TeamMemberDTO;
import com.cdac.cdachub.model.Project;
import com.cdac.cdachub.repository.ProjectRepository;
import com.cdac.cdachub.service.ProjectService;
import com.cdac.cdachub.utils.AuthUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;






@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    
   
    private static final String EMAIL_REGEX = "^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$";
    private boolean isValidEmail(String email) {
        return email != null && email.matches(EMAIL_REGEX);
    }

    // PUBLIC — anyone can browse
    @GetMapping("/public/projects")
    public ResponseEntity<Page<Project>> getApproved(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(projectRepository.findByStatus(Project.Status.APPROVED, pageable));
    }
    
    @GetMapping("/public/projects/year/{year}")
    public ResponseEntity<List<Project>> getByYear(@PathVariable Integer year) {
        return ResponseEntity.ok(projectService.getApprovedByYear(year));
    }
    
    // STUDENT — submit project (needs JWT)
    @PostMapping("/student/projects")
    public ResponseEntity<?> submit(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String techStack,
            @RequestParam String category,
            @RequestParam String gitLink,
            @RequestParam Integer year,
            @RequestParam String month,
            @RequestParam String submitterName,
            @RequestParam String submitterEmail,
            @RequestParam String submitterRollNo,
            @RequestParam String guideName,
            @RequestParam String guideEmail,
            @RequestParam(required = false) String teamMembers,
            @RequestParam List<MultipartFile> files) throws Exception {

        if (gitLink == null || gitLink.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Git link is required"));
        if (month == null || month.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Month is required"));
        if (year == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Year is required"));
        if (submitterRollNo == null || submitterRollNo.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Your roll number is required"));
        if (guideName == null || guideName.isBlank() || guideEmail == null || guideEmail.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Guide name and email are required"));

        
        if (!isValidEmail(submitterEmail) || !isValidEmail(guideEmail)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please enter valid email addresses"));
        }
        
        // Team members are sent as a JSON string inside the FormData — parse it here
        List<TeamMemberDTO> teamList = new ArrayList<>();
        if (teamMembers != null && !teamMembers.isBlank()) {
            ObjectMapper mapper = new ObjectMapper();
            teamList = mapper.readValue(teamMembers, new TypeReference<List<TeamMemberDTO>>() {});
            if (teamList.size() > 12)
                return ResponseEntity.badRequest().body(Map.of("error", "Maximum 12 team members allowed"));
        }
        
        
        for (TeamMemberDTO m : teamList) {
            if (!isValidEmail(m.getEmail())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid email for team member: " + m.getName()));
            }
        }
        
        
        

        String email = AuthUtil.getCurrentUserEmail();

        Project p = projectService.submitProject(
            title, description, techStack, category, gitLink, year, month,
            submitterName, submitterEmail, submitterRollNo,
            guideName, guideEmail, teamList,
            email, files);

        return ResponseEntity.ok(p);
    }

    // STUDENT — see my projects
    @GetMapping("/student/projects/mine")
    public ResponseEntity<List<Project>> myProjects() {
        String email = AuthUtil.getCurrentUserEmail();
        return ResponseEntity.ok(projectService.getMyProjects(email));
    }

    // REVIEWER — update project status
    @PutMapping("/reviewer/projects/{id}/status")
    public ResponseEntity<Project> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        Project.Status s = Project.Status.valueOf(status.toUpperCase());
        return ResponseEntity.ok(projectService.updateStatus(id, s));
    }
}