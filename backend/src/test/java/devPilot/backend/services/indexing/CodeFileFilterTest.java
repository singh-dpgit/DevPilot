package devPilot.backend.services.indexing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodeFileFilterTest {

    private final CodeFileFilter filter = new CodeFileFilter();
    private static final long DEFAULT_FILE_SIZE = 1024L;
    private static final long MAX_FILE_SIZE = 5242880L;

    @Test
    void shouldAcceptSupportedSourceFiles() {
        assertTrue(filter.isEligible("src/main/java/App.java", DEFAULT_FILE_SIZE, MAX_FILE_SIZE), "Should accept Java files");
        assertTrue(filter.isEligible("script.py", DEFAULT_FILE_SIZE, MAX_FILE_SIZE), "Should accept Python files");
    }

    @Test
    void shouldRejectExcludedDirectories() {
        assertFalse(filter.isEligible("node_modules/package/index.js", DEFAULT_FILE_SIZE, MAX_FILE_SIZE), "Should reject files in node_modules");
        assertFalse(filter.isEligible(".git/config", DEFAULT_FILE_SIZE, MAX_FILE_SIZE), "Should reject files in .git directory");
    }

    @Test
    void shouldRejectLockFiles() {
        assertFalse(filter.isEligible("package-lock.json", DEFAULT_FILE_SIZE, MAX_FILE_SIZE), "Should reject npm lock files");
        assertFalse(filter.isEligible("yarn.lock", DEFAULT_FILE_SIZE, MAX_FILE_SIZE), "Should reject yarn lock files");
    }

    @Test
    void shouldRejectOversizedFiles() {
        long oversized = MAX_FILE_SIZE + 1024L;
        assertFalse(filter.isEligible("src/Main.java", oversized, MAX_FILE_SIZE), "Should reject files exceeding max size limit");
    }
}