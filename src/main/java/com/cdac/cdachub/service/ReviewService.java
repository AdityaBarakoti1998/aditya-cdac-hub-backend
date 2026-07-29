package com.cdac.cdachub.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

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

    // Reviewer submits their verdict on a project
    public Review submitReview(Long projectId, String reviewerEmail,
                                String feedback, String verdict) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new RuntimeException("Reviewer not found"));

        // Save the review
        Review review = Review.builder()
                .project(project)
                .reviewer(reviewer)
                .feedback(feedback)
                .verdict(Review.Verdict.valueOf(verdict.toUpperCase()))
                .build();

        reviewRepository.save(review);

        // Update project status based on verdict
        if (verdict.equalsIgnoreCase("APPROVED")) {
            project.setStatus(Project.Status.APPROVED);
        } else {
            project.setStatus(Project.Status.REJECTED);
        }
        projectRepository.save(project);

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
}