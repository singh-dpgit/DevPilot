package devPilot.backend.services.indexing;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeChunkerTest {

    private final CodeFileFilter fileFilter = new CodeFileFilter();
    private final CodeChunker chunker = new CodeChunker(800, fileFilter);

    @Test
    void testChunkFileWithValidCode() {
        String repoId = "devpilot-repo";
        String filePath = "src/Main.java";
        String codeContent = "public class Main { public static void main(String[] args) {} }";

        List<Document> result = chunker.chunkFile(repoId, filePath, codeContent);

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isEmpty(), "Document list should contain at least one chunk");
        assertEquals(filePath, result.get(0).getMetadata().get("filePath"));
        assertEquals("java", result.get(0).getMetadata().get("language"));
    }

    @Test
    void testChunkFileWithEmptyContent() {
        List<Document> result = chunker.chunkFile("devpilot-repo", "src/Main.java", "");

        assertNotNull(result, "Result list should not be null");
        assertTrue(result.isEmpty(), "Result list should be empty for empty content");
    }
}