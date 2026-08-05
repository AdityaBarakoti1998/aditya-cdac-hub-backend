package com.cdac.cdachub.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cdac.cdachub.dto.TeamMemberDTO;
import com.cdac.cdachub.model.Project;
import com.cdac.cdachub.model.ProjectFile;
import com.cdac.cdachub.model.TeamMember;
import com.cdac.cdachub.model.User;
import com.cdac.cdachub.repository.ProjectFileRepository;
import com.cdac.cdachub.repository.ProjectRepository;
import com.cdac.cdachub.repository.TeamMemberRepository;
import com.cdac.cdachub.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EmailService emailService;
    @Value("${app.upload.dir}")
    private String uploadDir;

    @Transactional
    public Project submitProject(String title, String description,
            String techStack, String category, String gitLink,
            Integer year, String month,
            String submitterName, String submitterEmail, String submitterRollNo,
            String guideName, String guideEmail,
            List<TeamMemberDTO> teamMemberDtos,
            String userEmail, List<MultipartFile> files) throws IOException {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Write files to disk FIRST. If this fails, nothing has touched the DB yet.
        Files.createDirectories(Paths.get(uploadDir));
        List<String[]> savedFiles = new ArrayList<>(); // {originalName, storedUrl}
        for (MultipartFile file : files) {
            validateFileType(file); 
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path path = Paths.get(uploadDir, fileName);
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            savedFiles.add(new String[]{ file.getOriginalFilename(), "/uploads/" + fileName });
        }

        // All DB writes happen together — if ANY of these throw,
        // @Transactional rolls every DB row back. No half-saved project.
        Project project = Project.builder()
                .title(title).description(description).techStack(techStack).category(category)
                .gitLink(gitLink).year(year).month(month)
                .submitterName(submitterName).submitterEmail(submitterEmail).submitterRollNo(submitterRollNo)
                .guideName(guideName).guideEmail(guideEmail)
                .status(Project.Status.PENDING).user(user)
                .build();

        Project saved = projectRepository.save(project);

        if (teamMemberDtos != null) {
            for (TeamMemberDTO dto : teamMemberDtos) {
                teamMemberRepository.save(TeamMember.builder()
                        .name(dto.getName()).rollNo(dto.getRollNo()).email(dto.getEmail())
                        .project(saved).build());
            }
        }

        for (String[] f : savedFiles) {
            projectFileRepository.save(ProjectFile.builder()
                    .fileName(f[0]).fileUrl(f[1]).fileType("DOCS").project(saved).build());
        }
        
        emailService.sendSubmissionReceivedEmail(saved);
        emailService.notifyReviewersOfNewSubmission(saved, findReviewerEmailsForCategory(category));

        return saved;
    }
    
    @Transactional
    public void deleteProject(Long projectId) throws IOException {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getFiles() != null) {
            for (ProjectFile pf : project.getFiles()) {
                String storedFileName = pf.getFileUrl().replace("/uploads/", "");
                Files.deleteIfExists(Paths.get(uploadDir, storedFileName));
            }
        }

        projectRepository.delete(project); // still cascades files/team members/reviews in the DB
    }
    
    @Transactional
    public Project resubmitProject(Long projectId, String requesterEmail,
            String title, String description, String techStack, String category,
            String gitLink, Integer year, String month,
            String submitterName, String submitterEmail, String submitterRollNo,
            String guideName, String guideEmail,
            List<TeamMemberDTO> teamMemberDtos,
            List<MultipartFile> newFiles) throws IOException {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        // Ownership check
        if (!project.getUser().getEmail().equalsIgnoreCase(requesterEmail)) {
            throw new IllegalStateException("You can only resubmit your own project");
        }

        // State-machine check
        if (project.getStatus() != Project.Status.REJECTED) {
            throw new IllegalStateException("Only rejected projects can be resubmitted");
        }

        // Write any new files to disk first
        List<String[]> savedFiles = new ArrayList<>();
        if (newFiles != null && !newFiles.isEmpty()) {
            Files.createDirectories(Paths.get(uploadDir));
            for (MultipartFile file : newFiles) {
                validateFileType(file);
                String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
                Files.copy(file.getInputStream(), Paths.get(uploadDir, fileName), StandardCopyOption.REPLACE_EXISTING);
                savedFiles.add(new String[]{ file.getOriginalFilename(), "/uploads/" + fileName });
            }
        }

        project.setTitle(title);
        project.setDescription(description);
        project.setTechStack(techStack);
        project.setCategory(category);
        project.setGitLink(gitLink);
        project.setYear(year);
        project.setMonth(month);
        project.setSubmitterName(submitterName);
        project.setSubmitterEmail(submitterEmail);
        project.setSubmitterRollNo(submitterRollNo);
        project.setGuideName(guideName);
        project.setGuideEmail(guideEmail);
        project.setStatus(Project.Status.PENDING); // back into the review queue, fresh

        // ✅ JPA ANTI-PATTERN FIX: Safely update Team Members without crashing the DB
        if (project.getTeamMembers() != null) {
            project.getTeamMembers().clear(); // Safely empty the existing list
        }
        if (teamMemberDtos != null) {
            for (TeamMemberDTO dto : teamMemberDtos) {
                project.getTeamMembers().add(TeamMember.builder()
                        .name(dto.getName())
                        .rollNo(dto.getRollNo())
                        .email(dto.getEmail())
                        .project(project) // Link back to parent
                        .build());
            }
        }

        //  JPA ANTI-PATTERN FIX: Safely update Files without crashing the DB
        if (!savedFiles.isEmpty()) {
            if (project.getFiles() != null) {
                for (ProjectFile pf : project.getFiles()) {
                    Files.deleteIfExists(Paths.get(uploadDir, pf.getFileUrl().replace("/uploads/", "")));
                }
                project.getFiles().clear(); // Safely empty the existing list
            }
            for (String[] f : savedFiles) {
                project.getFiles().add(ProjectFile.builder()
                        .fileName(f[0])
                        .fileUrl(f[1])
                        .fileType("DOCS")
                        .project(project) // Link back to parent
                        .build());
            }
        }

        // Save everything in one smooth, safe transaction
        Project updated = projectRepository.save(project);
        emailService.sendSubmissionReceivedEmail(updated);
        emailService.notifyReviewersOfNewSubmission(updated, findReviewerEmailsForCategory(category));
        return updated;    }

    private static final List<String> ALLOWED_EXTENSIONS =
    	    List.of("pdf", "zip", "doc", "docx", "ppt", "pptx", "png", "jpg", "jpeg");

    private void validateFileType(MultipartFile file) {
        String name = file.getOriginalFilename();
        String ext = (name != null && name.contains("."))
            ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new RuntimeException("File type ." + ext + " is not allowed");
        }
    }

    public List<Project> getApprovedProjects() {
        return projectRepository.findByStatus(Project.Status.APPROVED);
    }
    
    
    private List<String> findReviewerEmailsForCategory(String category) {
        List<User> reviewers = userRepository.findByRole(User.Role.REVIEWER);
        List<String> matching = reviewers.stream()
                .filter(u -> u.getSpecializations() != null &&
                        Arrays.stream(u.getSpecializations().split(","))
                              .map(String::trim)
                              .anyMatch(c -> c.equalsIgnoreCase(category)))
                .map(User::getEmail)
                .toList();

        if (!matching.isEmpty()) return matching;

        // ✅ Nobody's covering this category yet — alert Admins instead
        // of letting the submission go silently unnoticed.
        return userRepository.findByRole(User.Role.ADMIN).stream()
                .map(User::getEmail)
                .toList();
    }
    

    public List<Project> getMyProjects(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return projectRepository.findByUserId(user.getId());
    }
    
    public List<Project> getApprovedByYear(Integer year) {
        return projectRepository.findByStatusAndYear("APPROVED", year);
    }

    public Project updateStatus(Long projectId, Project.Status status) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        project.setStatus(status);
        return projectRepository.save(project);
    }
}