package devPilot.backend.services.indexing;

import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.Repository;
import devPilot.backend.exceptions.BadRequestException;
import devPilot.backend.exceptions.NotFoundException;
import devPilot.backend.repository.RepositoryRepository;
import devPilot.backend.services.UserService;
import devPilot.backend.services.github.GitHubRateLimiter;
import devPilot.backend.services.github.GithubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private GithubApiClient gitHubApiClient;

    @Mock
    private CodeFileFilter fileFilter;

    @Mock
    private CodeChunker codeChunker;

    @Mock
    private GitHubRateLimiter rateLimiter;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private IndexingService indexingService;

    private UUID repoId;
    private UUID userId;
    private Repository mockRepo;

    @BeforeEach
    void setUp() {
        repoId = UUID.randomUUID();
        userId = UUID.randomUUID();

        mockRepo = new Repository();
        mockRepo.setId(repoId);
        mockRepo.setOwner("owner");
        mockRepo.setName("repo-name");
        mockRepo.setDefaultBranch("main");
        mockRepo.setIndexStatus(IndexStatus.FAILED);
    }

    @Test
    void shouldStartIndexingSuccessfully() {
        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(mockRepo));
        when(repositoryRepository.save(any(Repository.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Repository result = indexingService.startIndexing(repoId, userId);

        assertNotNull(result, "Repository result should not be null");
        assertEquals(IndexStatus.INDEXING, result.getIndexStatus());
        assertEquals(0, result.getFilesProcessed());
        assertEquals(0, result.getFilesTotal());
        assertEquals(0, result.getChunkCount());
        assertNull(result.getErrorMessage());
        verify(repositoryRepository, times(1)).save(mockRepo);
    }

    @Test
    void shouldThrowNotFoundExceptionWhenRepoNotFound() {
        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> indexingService.startIndexing(repoId, userId));
        verify(repositoryRepository, never()).save(any());
    }

    @Test
    void shouldThrowBadRequestWhenRepoAlreadyIndexing() {
        mockRepo.setIndexStatus(IndexStatus.INDEXING);
        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(mockRepo));

        assertThrows(BadRequestException.class, () -> indexingService.startIndexing(repoId, userId));
        verify(repositoryRepository, never()).save(any());
    }

    @Test
    void shouldProcessRepositoryIndexingSuccessfully() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(mockRepo));
        when(userService.decryptAccessToken(any())).thenReturn("decrypted-token");

        Map<String, Object> mockTree = Map.of(
            "tree", List.of(Map.of("type", "blob", "path", "src/Main.java", "size", 500L))
        );
        when(gitHubApiClient.getRepoTree("decrypted-token", "owner", "repo-name", "main")).thenReturn(mockTree);
        when(fileFilter.isEligible(eq("src/Main.java"), anyLong(), anyLong())).thenReturn(true);
        when(gitHubApiClient.getFileContent("decrypted-token", "owner", "repo-name", "src/Main.java")).thenReturn("code content");

        Document mockDocument = new Document("code content");
        when(codeChunker.chunkFile(repoId.toString(), "src/Main.java", "code content")).thenReturn(List.of(mockDocument));
        when(repositoryRepository.save(any(Repository.class))).thenAnswer(invocation -> invocation.getArgument(0));

        indexingService.indexAsync(repoId, userId);

        verify(vectorStore, times(1)).add(anyList());
        verify(rateLimiter, times(1)).pause();
        assertEquals(IndexStatus.READY, mockRepo.getIndexStatus());
        assertEquals(1, mockRepo.getFilesProcessed());
        assertEquals(1, mockRepo.getChunkCount());
    }

    @Test
    void shouldHandleFailureDuringIndexing() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(mockRepo));
        when(userService.decryptAccessToken(any())).thenThrow(new RuntimeException("Decryption error"));
        when(repositoryRepository.save(any(Repository.class))).thenAnswer(invocation -> invocation.getArgument(0));

        indexingService.indexAsync(repoId, userId);

        assertEquals(IndexStatus.FAILED, mockRepo.getIndexStatus());
        assertEquals("Decryption error", mockRepo.getErrorMessage());
    }
}