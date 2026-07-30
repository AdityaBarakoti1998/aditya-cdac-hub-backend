package com.cdac.cdachub.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cdac.cdachub.model.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByStatus(Project.Status status);
    List<Project> findByUserId(Long userId);
    List<Project> findByCategory(String category);
    
    List<Project> findByStatusAndYear(String status, Integer year);
    Page<Project> findByStatus(Project.Status status, Pageable pageable);
    
}