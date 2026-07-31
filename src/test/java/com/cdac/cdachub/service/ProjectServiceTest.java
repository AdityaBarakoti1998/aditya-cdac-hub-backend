package com.cdac.cdachub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cdac.cdachub.model.Project;
import com.cdac.cdachub.model.User;
import com.cdac.cdachub.repository.ProjectFileRepository;
import com.cdac.cdachub.repository.ProjectRepository;
import com.cdac.cdachub.repository.TeamMemberRepository;
import com.cdac.cdachub.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectFileRepository projectFileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void testGetApprovedProjects() {

        Project p1 = new Project();
        p1.setTitle("Library");

        Project p2 = new Project();
        p2.setTitle("Hospital");

        when(projectRepository.findByStatus(Project.Status.APPROVED))
                .thenReturn(Arrays.asList(p1, p2));

        List<Project> result = projectService.getApprovedProjects();

        assertEquals(2, result.size());

        verify(projectRepository, times(1))
                .findByStatus(Project.Status.APPROVED);
    }
    @Test
    void testGetMyProjects() {

        User user = new User();
        user.setId(1L);
        user.setEmail("test@gmail.com");

        Project p1 = new Project();
        p1.setTitle("Project A");

        Project p2 = new Project();
        p2.setTitle("Project B");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        when(projectRepository.findByUserId(1L))
                .thenReturn(Arrays.asList(p1, p2));

        List<Project> result = projectService.getMyProjects("test@gmail.com");

        assertEquals(2, result.size());

        verify(userRepository).findByEmail("test@gmail.com");
        verify(projectRepository).findByUserId(1L);
    }
    @Test
    void testGetMyProjects_UserNotFound() {

        when(userRepository.findByEmail("abc@gmail.com"))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> projectService.getMyProjects("abc@gmail.com"));

        assertEquals("User not found", ex.getMessage());

        verify(userRepository).findByEmail("abc@gmail.com");
    }
    @Test
    void testGetApprovedByYear() {

        Project project = new Project();

        when(projectRepository.findByStatusAndYear("APPROVED", 2026))
                .thenReturn(Arrays.asList(project));

        List<Project> result = projectService.getApprovedByYear(2026);

        assertEquals(1, result.size());

        verify(projectRepository)
                .findByStatusAndYear("APPROVED", 2026);
    }
    @Test
    void testUpdateStatus_Success() {

        Project project = new Project();
        project.setStatus(Project.Status.PENDING);

        when(projectRepository.findById(1L))
                .thenReturn(Optional.of(project));

        when(projectRepository.save(any(Project.class)))
                .thenReturn(project);

        Project updated =
                projectService.updateStatus(1L, Project.Status.APPROVED);

        assertEquals(Project.Status.APPROVED, updated.getStatus());

        verify(projectRepository).findById(1L);
        verify(projectRepository).save(project);
    }
    @Test
    void testUpdateStatus_ProjectNotFound() {

        when(projectRepository.findById(100L))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> projectService.updateStatus(
                        100L,
                        Project.Status.APPROVED));

        assertEquals("Project not found", ex.getMessage());

        verify(projectRepository).findById(100L);
    }
    @Test
    void testDeleteProject_ProjectNotFound() {

        when(projectRepository.findById(10L))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> projectService.deleteProject(10L));

        assertEquals("Project not found", ex.getMessage());

        verify(projectRepository).findById(10L);
        verify(projectRepository, never()).delete(any(Project.class));
    }
}