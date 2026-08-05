package com.cdac.cdachub.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.Version;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    
    
 // JPA's built-in optimistic locking. Every UPDATE checks this value
 // hasn't changed since it was read — if two reviewers (or a reviewer
 // + admin) load the same project and both try to act on it, the
 // second write fails cleanly instead of silently overwriting the first.
 @Version
 private Long version;
 

    @Column(columnDefinition = "TEXT")
    private String description;

    private String techStack;
    private String category;

    //  Replaces "price"
    @Column(nullable = false)
    private String gitLink;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private String month;

    //  NEW — captured fresh at submission time
    private String submitterName;
    private String submitterEmail;
    private String submitterRollNo;

    //  NEW — guide details
    private String guideName;
    private String guideEmail;

    @Enumerated(EnumType.STRING)
    private Status status;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"password", "googleId"})
    private User user;

 //  ADDED: orphanRemoval = true 
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("project")
    private List<ProjectFile> files;

    //  NEW — 0 to 12 team members
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("project")
    private List<TeamMember> teamMembers;
    
 //  NEW — deleting a project now also deletes its review history
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("project")
    private List<Review> reviews;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public enum Status {
        PENDING, UNDER_REVIEW, APPROVED, REJECTED
    }
}