
package devPilot.backend.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import devPilot.backend.dto.IndexStatusResponse;
import devPilot.backend.dto.RepositoryResponse;
import devPilot.backend.entity.Repository;
import devPilot.backend.security.CurrentUser;
import devPilot.backend.services.RepoService;
import devPilot.backend.services.indexing.IndexingService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final CurrentUser currentUser;
    private final RepoService repoService;
    private final IndexingService indexingService;

    @GetMapping
    public List<RepositoryResponse> list(
            @RequestParam(name = "refresh", defaultValue = "true") boolean refresh) {

        UUID userId = currentUser.require().getId();

        if (refresh) {
            return repoService.syncAndListRepos(userId);
        }

        return repoService.listStored(userId);
    }

    @GetMapping("/{id}")
    public RepositoryResponse get(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();

        return repoService.toResponse(
                repoService.requireOwned(id, userId));
    }

    @GetMapping("/{id}/status")
    public IndexStatusResponse status(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();

        return repoService.status(id, userId);
    }

    // Starts indexing for the selected repository.
    @PostMapping("/{id}/index")
    public RepositoryResponse index(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();

        // Verify ownership and mark the repository as INDEXING.
        Repository repo = indexingService.startIndexing(id, userId);

        // Run the actual indexing process asynchronously.
        indexingService.indexAsync(id, userId);

        // Return the repository's current indexing status.
        return repoService.toResponse(repo);
    }
}
