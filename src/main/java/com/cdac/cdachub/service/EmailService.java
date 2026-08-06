package com.cdac.cdachub.service;

import com.cdac.cdachub.model.Project;
import com.cdac.cdachub.model.Review;
import com.cdac.cdachub.model.TeamMember;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Async("emailTaskExecutor")
    public void sendSubmissionReceivedEmail(Project project) {
        String subject = "CDACHub — We received your project: " + escapeHtml(project.getTitle());
        String body = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
                <h2 style="color:#7c3aed;">CDACHub</h2>
                <p>Hi %s,</p>
                <p>We've received your project <strong>"%s"</strong>.
                It's now pending review by a specialist in <strong>%s</strong>.</p>
                <p>You'll get another email as soon as a decision is made.</p>
                <p style="color:#888;font-size:12px;margin-top:30px;">— CDACHub</p>
            </div>
            """.formatted(escapeHtml(project.getSubmitterName()), escapeHtml(project.getTitle()),
                           escapeHtml(project.getCategory()));

        sendHtmlEmail(project.getSubmitterEmail(), null, subject, body);
    }

    @Async("emailTaskExecutor")
    public void notifyReviewersOfNewSubmission(Project project, List<String> reviewerEmails) {
        if (reviewerEmails == null || reviewerEmails.isEmpty()) return;

        String subject = "New " + project.getCategory() + " project awaiting review: " + escapeHtml(project.getTitle());
        String body = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
                <h2 style="color:#7c3aed;">CDACHub — New Project to Review</h2>
                <p>A new <strong>%s</strong> project is waiting for your review:</p>
                <p><strong>%s</strong><br/>%s</p>
                <p>Submitted by: %s</p>
                <p><a href="%s" style="color:#7c3aed;">Log in to review it</a></p>
            </div>
            """.formatted(escapeHtml(project.getCategory()), escapeHtml(project.getTitle()),
                           escapeHtml(project.getDescription()), escapeHtml(project.getSubmitterName()),
                           frontendUrl + "/reviewer");

        for (String reviewerEmail : reviewerEmails) {
            sendHtmlEmail(reviewerEmail, null, subject, body);
        }
    }

    @Async("emailTaskExecutor")
    public void sendReviewDecisionEmail(Project project, Review review) {
        boolean approved = review.getVerdict() == Review.Verdict.APPROVED;

        String subject = approved
            ? "🎉 Your project was approved: " + escapeHtml(project.getTitle())
            : "Action needed on your project: " + escapeHtml(project.getTitle());

        String body = approved
            ? """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
                    <h2 style="color:#16a34a;">🎉 Project Approved!</h2>
                    <p>Hi %s,</p>
                    <p>Great news — <strong>"%s"</strong> has been approved and is now
                    published on CDACHub for everyone to see.</p>
                    <p><a href="%s" style="color:#16a34a;">View it live</a></p>
                    <p style="color:#888;font-size:12px;margin-top:30px;">— CDACHub</p>
                </div>
                """.formatted(escapeHtml(project.getSubmitterName()), escapeHtml(project.getTitle()), frontendUrl)
            : """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
                    <h2 style="color:#dc2626;">Action Needed on Your Project</h2>
                    <p>Hi %s,</p>
                    <p><strong>"%s"</strong> was reviewed and needs some changes before
                    it can be approved.</p>
                    <p style="background:#fef2f2;padding:12px;border-radius:8px;color:#7f1d1d;">%s</p>
                    <p>Log in to your dashboard to fix and resubmit.</p>
                    <p style="color:#888;font-size:12px;margin-top:30px;">— CDACHub</p>
                </div>
                """.formatted(escapeHtml(project.getSubmitterName()), escapeHtml(project.getTitle()),
                               escapeHtml(review.getFeedback()));

        // Cc: guide + team members, deduped against the submitter so
        // nobody's own email lands in both To and Cc at once.
        List<String> cc = new ArrayList<>();
        if (project.getGuideEmail() != null && !project.getGuideEmail().equalsIgnoreCase(project.getSubmitterEmail())) {
            cc.add(project.getGuideEmail());
        }
        if (project.getTeamMembers() != null) {
            for (TeamMember tm : project.getTeamMembers()) {
                if (!tm.getEmail().equalsIgnoreCase(project.getSubmitterEmail())) {
                    cc.add(tm.getEmail());
                }
            }
        }

        sendHtmlEmail(project.getSubmitterEmail(), cc, subject, body);
    }

    private void sendHtmlEmail(String to, List<String> cc, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            if (cc != null && !cc.isEmpty()) {
                helper.setCc(cc.toArray(new String[0]));
            }
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent: '{}' to {}", subject, to);
        } catch (Exception e) {
            //  Runs on a background thread — nothing is waiting on this,
            // so we never let a mail failure surface as a user-facing error.
            // Just log it clearly so you notice if SMTP ever misbehaves.
            log.error("Failed to send email '{}' to {}: {}", subject, to, e.getMessage());
        }
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}