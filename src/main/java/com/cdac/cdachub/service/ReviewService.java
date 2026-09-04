package com.cdac.cdachub.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdac.cdachub.model.Project;
import com.cdac.cdachub.model.Review;
import com.cdac.cdachub.model.User;
import com.cdac.cdachub.repository.ProjectRepository;
import com.cdac.cdachub.repository.ReviewRepository;
import com.cdac.cdachub.repository.UserRepository;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final RepoIndexingService repoIndexingService;
    // Reviewer submits their verdict on a project
    @Transactional
    public Review submitReview(Long projectId, String reviewerEmail, String feedback, String verdict) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        //  Fix 3 — state-machine guard: someone may have already decided
        // this project between when the reviewer's page loaded and now.
        if (project.getStatus() != Project.Status.PENDING) {
            throw new IllegalStateException(
                "This project has already been reviewed (current status: " + project.getStatus() + ")");
        }

        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new RuntimeException("Reviewer not found"));

        //  Fix 7 — conflict of interest: can't review your own submission
        if (project.getUser().getEmail().equalsIgnoreCase(reviewerEmail)) {
            throw new IllegalStateException("You cannot review your own submitted project");
        }

        Review review = Review.builder()
                .project(project)
                .reviewer(reviewer)
                .feedback(feedback)
                .verdict(Review.Verdict.valueOf(verdict.toUpperCase()))
                .build();
        reviewRepository.save(review);

        project.setStatus(verdict.equalsIgnoreCase("APPROVED") ? Project.Status.APPROVED : Project.Status.REJECTED);
        if (verdict.equalsIgnoreCase("APPROVED")) {
            repoIndexingService.indexProject(project.getId());
        }

        // This save is where @Version does its real work: if another
        // transaction already modified this row since we loaded it,
        // Hibernate throws ObjectOptimisticLockingFailureException here.
        
        //  Fix 4 — optimistic locking: if another reviewer already submitted a verdict, this save will fail
        Project savedProject = projectRepository.save(project);
        emailService.sendReviewDecisionEmail(savedProject, review);
        return review;
    }

    // Get all pending projects (for reviewer to see what needs review)
 // Reviewer sees only projects in their specialization; Admin sees everything
    public List<Project> getPendingProjectsForReviewer(String reviewerEmail) {
        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Project> pending = projectRepository.findByStatus(Project.Status.PENDING);

        if (reviewer.getRole() == User.Role.ADMIN) {
            return pending;
        }

        String specializations = reviewer.getSpecializations();
        if (specializations == null || specializations.isBlank()) {
            return List.of(); // not assigned a category yet — sees nothing until admin sets one
        }

        List<String> categories = Arrays.stream(specializations.split(","))
                .map(String::trim)
                .toList();

        return pending.stream()
                .filter(p -> categories.contains(p.getCategory()))
                .toList();
    }

    // Get all reviews for a project
    public List<Review> getReviewsForProject(Long projectId) {
        return reviewRepository.findByProjectId(projectId);
    }
    
    public Project getProjectForReviewer(Long projectId, String reviewerEmail) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        //  Admins can open anything. Reviewers are still bound to their
        // assigned categories — a deep link can't be used to bypass
        // category routing. A legitimate email link always passes this,
        // since the category matched at the moment the email was sent.
        if (reviewer.getRole() != User.Role.ADMIN) {
            List<String> categories = reviewer.getSpecializations() == null
                    ? List.of()
                    : Arrays.stream(reviewer.getSpecializations().split(",")).map(String::trim).toList();

            if (!categories.contains(project.getCategory())) {
                throw new org.springframework.security.access.AccessDeniedException(
                    "This project isn't in your assigned category");
            }
        }
        return project;
    }
    
}