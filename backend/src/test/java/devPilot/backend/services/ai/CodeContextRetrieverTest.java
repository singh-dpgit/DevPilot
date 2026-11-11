package devPilot.backend.services.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import devPilot.backend.dto.CitationDto;
import devPilot.backend.services.ai.CitationMapper;
import devPilot.backend.services.ai.CodeContextRetriever;
import devPilot.backend.services.ai.RetrievedContext;

@ExtendWith(MockitoExtension.class)
class CodeContextRetrieverTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private CitationMapper citationMapper;

    @InjectMocks
    private CodeContextRetriever retriever;

    @Test
    void testRetrieve_WithMatches() {
        // Inputs
        UUID repoId = UUID.randomUUID();
        String question = "How pagination works?";
        
        // Mock data
        Document mockDoc = new Document("PageRequest implementation", Map.of("filePath", "Page.java"));
        CitationDto mockCitation = new CitationDto("Page.java", 1, 10, "java");

        // Stubbing
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(mockDoc));
        when(citationMapper.fromDocument(mockDoc)).thenReturn(mockCitation);

        // Execute
        RetrievedContext result = retriever.retrieve(repoId, question);

        // Assert - using record accessor methods () instead of get()
        assertEquals("PageRequest implementation", result.contextText()); 
        assertEquals(1, result.citations().size());

        // Verify
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        verify(citationMapper).fromDocument(mockDoc);
    }

    @Test
    void testRetrieve_NoMatches() {
        UUID repoId = UUID.randomUUID();
        
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        RetrievedContext result = retriever.retrieve(repoId, "unknown");

        assertEquals("(no matching code chunks found)", result.contextText());
        assertTrue(result.citations().isEmpty());
    }
}