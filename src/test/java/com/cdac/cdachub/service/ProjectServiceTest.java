package com.cdac.cdachub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdac.cdachub.model.Project;
import com.cdac.cdachub.model.ProjectFile;
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

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ProjectService projectService;

    @TempDir
    Path tempDir;


    // =========================================================
    // 1. Submit Project - Success
    // =========================================================

    @Test
    void testSubmitProject_Success() throws Exception {

        ReflectionTestUtils.setField(
                projectService,
                "uploadDir",
                tempDir.toString()
        );

        User user = new User();
        user.setEmail("test@gmail.com");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        Project savedProject = new Project();

        when(projectRepository.save(any(Project.class)))
                .thenReturn(savedProject);

        when(projectFileRepository.save(any(ProjectFile.class)))
                .thenReturn(null);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "project.pdf",
                "application/pdf",
                "Hello World".getBytes()
        );

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
                List.of(file)
        );

        assertNotNull(result);

        verify(userRepository)
                .findByEmail("test@gmail.com");

        verify(projectRepository)
                .save(any(Project.class));

        verify(projectFileRepository)
                .save(any(ProjectFile.class));

        verify(emailService)
                .sendSubmissionReceivedEmail(savedProject);

        verify(emailService)
                .notifyReviewersOfNewSubmission(
                        any(Project.class),
                        any(List.class)
                );
    }


    // =========================================================
    // 2. Submit Project - Invalid File Extension
    // =========================================================

    @Test
    void testSubmitProject_ThrowsException_OnInvalidExtension()
            throws Exception {

        ReflectionTestUtils.setField(
                projectService,
                "uploadDir",
                tempDir.toString()
        );

        User user = new User();
        user.setEmail("test@gmail.com");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "virus.exe",
                "application/octet-stream",
                "dummy".getBytes()
        );

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
                        List.of(file)
                )
        );

        assertEquals(
                "File type .exe is not allowed",
                ex.getMessage()
        );

        verify(userRepository)
                .findByEmail("test@gmail.com");

        verify(projectRepository, never())
                .save(any(Project.class));

        verify(projectFileRepository, never())
                .save(any(ProjectFile.class));
    }


    // =========================================================
    // 3. Submit Project - Transaction Rollback /
    //    Disk Write Failure
    // =========================================================

    @Test
    void testSubmitProject_TransactionRollback_OnDiskWriteFailure()
            throws Exception {

        User user = new User();
        user.setEmail("test@gmail.com");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        /*
         * Create a FILE and use its path as uploadDir.
         * ProjectService calls Files.createDirectories(uploadDir),
         * which will fail because uploadDir already exists as a file.
         */
        Path uploadPathThatIsActuallyAFile =
                Files.createTempFile(tempDir, "upload", ".tmp");

        ReflectionTestUtils.setField(
                projectService,
                "uploadDir",
                uploadPathThatIsActuallyAFile.toString()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "project.pdf",
                "application/pdf",
                "test content".getBytes()
        );

        assertThrows(
                IOException.class,
                () -> projectService.submitProject(
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
                        List.of(file)
                )
        );

        /*
         * Disk operation failed BEFORE database save.
         * Therefore no Project should have been saved.
         */
        verify(projectRepository, never())
                .save(any(Project.class));

        verify(projectFileRepository, never())
                .save(any(ProjectFile.class));

        verify(teamMemberRepository, never())
                .save(any());

        verify(emailService, never())
                .sendSubmissionReceivedEmail(any(Project.class));

        verify(emailService, never())
                .notifyReviewersOfNewSubmission(
                        any(Project.class),
                        any(List.class)
                );
    }


    // =========================================================
    // 4. Delete Project - Success
    // =========================================================

    @Test
    void testDeleteProject_Success() throws Exception {

        Project project = new Project();

        when(projectRepository.findById(1L))
                .thenReturn(Optional.of(project));

        projectService.deleteProject(1L);

        verify(projectRepository)
                .findById(1L);

        verify(projectRepository)
                .delete(project);
    }


    // =========================================================
    // 5. Delete Project - Project Not Found
    // =========================================================

    @Test
    void testDeleteProject_ThrowsException_ProjectNotFound()
            throws Exception {

        when(projectRepository.findById(100L))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> projectService.deleteProject(100L)
        );

        assertEquals(
                "Project not found",
                ex.getMessage()
        );

        verify(projectRepository)
                .findById(100L);

        verify(projectRepository, never())
                .delete(any(Project.class));
    }
}