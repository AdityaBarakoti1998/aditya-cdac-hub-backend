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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdac.cdachub.model.Project;
import com.cdac.cdachub.model.User;
import com.cdac.cdachub.repository.ProjectFileRepository;
import com.cdac.cdachub.repository.ProjectRepository;
import com.cdac.cdachub.repository.TeamMemberRepository;
import com.cdac.cdachub.repository.UserRepository;
import com.cdac.cdachub.model.ProjectFile;

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
    @Test
    void testDeleteProject_Success() throws Exception {

        Project project = new Project();

        when(projectRepository.findById(1L))
                .thenReturn(Optional.of(project));

        projectService.deleteProject(1L);

        verify(projectRepository).findById(1L);
        verify(projectRepository).delete(project);
    }
    @Test
    void testUpdateStatus_SaveCalled() {

        Project project = new Project();

        when(projectRepository.findById(5L))
                .thenReturn(Optional.of(project));

        when(projectRepository.save(any(Project.class)))
                .thenReturn(project);

        projectService.updateStatus(5L, Project.Status.REJECTED);

        verify(projectRepository).findById(5L);
        verify(projectRepository).save(project);

        assertEquals(Project.Status.REJECTED, project.getStatus());
    }
    @Test
    void testSubmitProject_Success() throws Exception {

        ReflectionTestUtils.setField(
                projectService,
                "uploadDir",
                System.getProperty("java.io.tmpdir"));

        User user = new User();
        user.setEmail("test@gmail.com");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        Project savedProject = new Project();

        when(projectRepository.save(any(Project.class)))
                .thenReturn(savedProject);

        when(projectFileRepository.save(any()))
                .thenReturn(null);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "project.pdf",
                "application/pdf",
                "Hello World".getBytes());

        Project result = projectService.submitProject(
                "Library System",
                "Description",
                "Java",
                "Web",
                "https://github.com/test",
                2026,
                "July",
                "Aditya",
                "aditya@gmail.com",
                "123",
                "Guide",
                "guide@gmail.com",
                null,
                "test@gmail.com",
                List.of(file));

        assertNotNull(result);

        verify(userRepository).findByEmail("test@gmail.com");
        verify(projectRepository).save(any(Project.class));
        verify(projectFileRepository).save(any());
    }
    @Test
    void testSubmitProject_ThrowsException_OnInvalidExtension() {

        ReflectionTestUtils.setField(
                projectService,
                "uploadDir",
                System.getProperty("java.io.tmpdir"));

        User user = new User();
        user.setEmail("test@gmail.com");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "virus.exe",
                "application/octet-stream",
                "dummy".getBytes());

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> projectService.submitProject(
                        "Title",
                        "Description",
                        "Java",
                        "Web",
                        "https://github.com/test",
                        2026,
                        "July",
                        "Aditya",
                        "aditya@gmail.com",
                        "123",
                        "Guide",
                        "guide@gmail.com",
                        null,
                        "test@gmail.com",
                        List.of(file)));

        assertEquals("File type .exe is not allowed", ex.getMessage());

        verify(userRepository).findByEmail("test@gmail.com");
        verify(projectRepository, never()).save(any(Project.class));
        verify(projectFileRepository, never()).save(any(ProjectFile.class));
    }
}