package com.cdac.cdachub.controller;

import com.cdac.cdachub.model.*;
import com.cdac.cdachub.service.ReviewService;
import com.cdac.cdachub.utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ReviewController {

    private final ReviewService reviewService;

    // Reviewer sees pending projects
    @GetMapping("/reviewer/projects/pending")
    public ResponseEntity<List<Project>> getPending() {
        String email = AuthUtil.getCurrentUserEmail();
        return ResponseEntity.ok(reviewService.getPendingProjectsForReviewer(email));
    }

    // Reviewer submits verdict
    @PostMapping("/reviewer/projects/{projectId}/review")
    public ResponseEntity<Review> submitReview(
            @PathVariable Long projectId,
            @RequestParam String feedback,
            @RequestParam String verdict) {

        // Get reviewer email from JWT
        String email = AuthUtil.getCurrentUserEmail();

        Review review = reviewService.submitReview(
            projectId, email, feedback, verdict);
        
        

        return ResponseEntity.ok(review);
    }

    // Public — see reviews of a project
    @GetMapping("/public/projects/{projectId}/reviews")
    public ResponseEntity<List<Review>> getReviews(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(
            reviewService.getReviewsForProject(projectId));
    }
}