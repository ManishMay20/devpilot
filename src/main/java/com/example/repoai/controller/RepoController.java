package com.example.repoai.controller;

import com.example.repoai.entity.RepositoryEntity;
import com.example.repoai.entity.User;
import com.example.repoai.security.AppUserPrincipal;
import com.example.repoai.service.RepoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for repository management (list, sync, index, status).
 */
@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;
    private final com.example.repoai.service.indexing.IndexingService indexingService;

    // ── List / Sync ───────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<RepositoryEntity>> listRepos(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(name = "sync", defaultValue = "false") boolean sync) {

        if (principal == null) return ResponseEntity.status(401).build();

        User user = principal.getUser();
        List<RepositoryEntity> repos;

        if (sync) {
            repos = repoService.syncAndListRepos(user);
        } else {
            repos = repoService.getUserRepos(user.getId());
            if (repos.isEmpty()) {
                repos = repoService.syncAndListRepos(user);
            }
        }
        return ResponseEntity.ok(repos);
    }

    // ── Indexing ──────────────────────────────────────────────────────────────

    /**
     * Start indexing. Will be a no-op if the repo is already INDEXING (safe to call).
     */
    @PostMapping("/{id}/index")
    public ResponseEntity<RepositoryEntity> startIndexing(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        if (principal == null) return ResponseEntity.status(401).build();

        RepositoryEntity repo = indexingService.startIndexing(id, principal.getUser(), false);
        return ResponseEntity.ok(repo);
    }

    /**
     * Force re-index – resets the repo even if it is currently INDEXING or FAILED.
     * Use this from the UI "Force Re-index" button to recover from stuck states.
     */
    @PostMapping("/{id}/reindex")
    public ResponseEntity<RepositoryEntity> forceReIndex(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        if (principal == null) return ResponseEntity.status(401).build();

        RepositoryEntity repo = indexingService.startIndexing(id, principal.getUser(), true);
        return ResponseEntity.ok(repo);
    }

    // ── Status polling ────────────────────────────────────────────────────────

    /**
     * Lightweight status endpoint for the UI to poll every few seconds while
     * a repository is being indexed.
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<RepositoryEntity> getRepoStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        if (principal == null) return ResponseEntity.status(401).build();

        return repoService.getUserRepos(principal.getUser().getId()).stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}