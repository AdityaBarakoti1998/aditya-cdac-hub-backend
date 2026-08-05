package com.cdac.cdachub.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    
    // Server-side validation list
    private static final List<String> VALID_CATEGORIES = List.of(
        "AI & ML", "Web Dev", "Mobile Apps", "Cybersecurity",
        "Cloud & DevOps", "Data Science", "Blockchain", "IoT"
    );

    // =========================================================================
    // ✅ HELPER 1: Single Shared Validator (Fixes Validation Drift)
    // =========================================================================
    private String validateProjectFields(String gitLink, String category, Integer year, String month, 
                                         String submitterEmail, String submitterRollNo, 
                                         String guideName, String guideEmail, List<TeamMemberDTO> teamList) {
        if (gitLink == null || gitLink.isBlank()) return "Git link is required";
        
        
     // Strict URL format check to ensure a valid web link is provided
        if (!gitLink.matches("^https?://.+")) return "Git link must be a valid URL";
        
        if (!VALID_CATEGORIES.contains(category)) return "Invalid category: " + category;
        if (year == null) return "Year is required";
        if (month == null || month.isBlank()) return "Month is required";
        if (submitterRollNo == null || submitterRollNo.isBlank()) return "Your roll number is required";
        if (!isValidEmail(submitterEmail)) return "Please enter a valid email for yourself";
        if (guideName == null || guideName.isBlank()) return "Guide name is required";
        if (!isValidEmail(guideEmail)) return "Please enter a valid guide email";
        if (teamList.size() > 12) return "Maximum 12 team members allowed";
        
        for (TeamMemberDTO m : teamList) {
            if (!isValidEmail(m.getEmail())) return "Invalid email for team member: " + m.getName();
        }
        return null; // Null means no errors, validation passed!
    }

    // =========================================================================
    // ✅ HELPER 2: JSON Parser for Team Members
    // =========================================================================
    private List<TeamMemberDTO> parseTeamMembers(String teamMembersJson) {
        if (teamMembersJson == null || teamMembersJson.isBlank()) return new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(teamMembersJson, new TypeReference<List<TeamMemberDTO>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid team member data format");
        }
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
    
    // =========================================================================
    //  UPDATED: STUDENT — Submit New Project (Now uses helpers)
    // =========================================================================
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

        List<TeamMemberDTO> teamList = parseTeamMembers(teamMembers);
        
        String validationError = validateProjectFields(gitLink, category, year, month, 
                                                       submitterEmail, submitterRollNo, 
                                                       guideName, guideEmail, teamList);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        String email = AuthUtil.getCurrentUserEmail();
        Project p = projectService.submitProject(
            title, description, techStack, category, gitLink, year, month,
            submitterName, submitterEmail, submitterRollNo,
            guideName, guideEmail, teamList,
            email, files);

        return ResponseEntity.ok(p);
    }

    // =========================================================================
    //  NEW: STUDENT — Edit & Resubmit a rejected project
    // =========================================================================
    @PutMapping("/student/projects/{id}")
    public ResponseEntity<?> resubmit(
            @PathVariable Long id,
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
            @RequestParam(required = false) List<MultipartFile> files) throws Exception {

        List<TeamMemberDTO> teamList = parseTeamMembers(teamMembers);
        
        String validationError = validateProjectFields(gitLink, category, year, month, 
                                                       submitterEmail, submitterRollNo, 
                                                       guideName, guideEmail, teamList);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        String email = AuthUtil.getCurrentUserEmail();
        Project p = projectService.resubmitProject(
            id, email, title, description, techStack, category, gitLink, year, month, 
            submitterName, submitterEmail, submitterRollNo, guideName, guideEmail, 
            teamList, files);

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