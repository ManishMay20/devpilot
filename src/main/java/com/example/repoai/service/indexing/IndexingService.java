package com.example.repoai.service.indexing;

import com.example.repoai.entity.IndexStatus;
import com.example.repoai.entity.RepositoryEntity;
import com.example.repoai.entity.User;
import com.example.repoai.repository.RepoRepository;
import com.example.repoai.service.github.GithubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {

    /** Flush a batch to the vector store every N chunks to minimise Ollama round-trips. */
    private static final int BATCH_SIZE = 30;

    private final RepoRepository repoRepository;
    private final GithubApiClient githubApiClient;
    private final CodeFileFilter fileFilter;
    private final CodeChunker codeChunker;
    private final VectorStore vectorStore;

    // ─────────────────────────────────────────────────────────────────────────
    //  Public entry-points
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start (or force-restart) indexing for a repository.
     *
     * <p>Allowed from any status – if the caller passes {@code force=true} even a
     * repo that is currently {@code INDEXING} will be reset and re-indexed.  This
     * prevents the UI from being stuck forever when a previous async task died
     * silently.</p>
     */
    public RepositoryEntity startIndexing(UUID repoId, User user) {
        return startIndexing(repoId, user, false);
    }

    public RepositoryEntity startIndexing(UUID repoId, User user, boolean force) {
        RepositoryEntity repo = repoRepository.findByIdAndUserId(repoId, user.getId())
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        // Guard: don't double-trigger unless explicitly forced
        if (!force && repo.getIndexStatus() == IndexStatus.INDEXING) {
            log.warn("Repo {} is already INDEXING – skipping duplicate trigger. Use force=true to override.", repoId);
            return repo;
        }

        // Clear any stale error message, reset counters, and mark as in-progress
        repo.setIndexStatus(IndexStatus.INDEXING);
        repo.setErrorMessage(null);
        repo.setFilesTotal(0);
        repo.setFilesProcessed(0);
        repo.setChunkCount(0);
        repoRepository.save(repo);

        // Trigger background worker
        indexRepositoryAsync(repo.getId(), user.getAccessToken());

        return repo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Async indexing pipeline
    // ─────────────────────────────────────────────────────────────────────────

    @Async
    public void indexRepositoryAsync(UUID repoId, String accessToken) {
        RepositoryEntity repo = repoRepository.findById(repoId).orElse(null);
        if (repo == null) {
            log.error("Repo not found for repoId: {}", repoId);
            return;
        }

        log.info("Starting indexing pipeline for repository: {}", repo.getFullName());

        try {
            String owner  = repo.getOwner();
            String branch = (repo.getDefaultBranch() != null && !repo.getDefaultBranch().isBlank())
                    ? repo.getDefaultBranch()
                    : "main";

            // ── 1. Fetch full file tree from GitHub ──────────────────────────
            Map<String, Object> treeData = githubApiClient.getRepoTree(
                    accessToken, owner, repo.getName(), branch);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tree = (List<Map<String, Object>>) treeData.get("tree");
            if (tree == null || tree.isEmpty()) {
                throw new RuntimeException("Empty repository tree or branch '" + branch + "' not found.");
            }

            log.info("Total items in tree: {}", tree.size());

            // ── 2. Aggressive file filtering – only core source files ────────
            List<Map<String, Object>> eligibleFiles = new ArrayList<>();
            for (Map<String, Object> item : tree) {
                String type = (String) item.get("type"); // "blob" = file
                String path = (String) item.get("path");
                Number size = (Number) item.get("size");

                if ("blob".equals(type) && path != null) {
                    long sizeBytes = size != null ? size.longValue() : 0;
                    if (fileFilter.isEligible(path, sizeBytes)) {
                        eligibleFiles.add(item);
                    }
                }
            }

            log.info("Eligible source files to index: {} / {}", eligibleFiles.size(), tree.size());

            if (eligibleFiles.isEmpty()) {
                // Nothing to index – treat as READY with 0 chunks
                repo.setIndexStatus(IndexStatus.READY);
                repo.setIndexedAt(Instant.now());
                repo.setFilesTotal(0);
                repo.setFilesProcessed(0);
                repo.setChunkCount(0);
                repoRepository.save(repo);
                log.warn("No eligible source files found in {}. Marked READY with 0 chunks.", repo.getFullName());
                return;
            }

            repo.setFilesTotal(eligibleFiles.size());
            repo.setFilesProcessed(0);
            repoRepository.save(repo);

            // ── 3. Fetch, chunk, and batch-ingest into PGVector ─────────────
            List<Document> batch        = new ArrayList<>(BATCH_SIZE);
            int            processedCount = 0;
            int            totalChunks    = 0;
            int            failedFiles    = 0;

            for (Map<String, Object> fileItem : eligibleFiles) {
                String path = (String) fileItem.get("path");
                try {
                    String content = githubApiClient.getFileContent(
                            accessToken, owner, repo.getName(), path);

                    List<Document> chunks = codeChunker.chunkFile(repo.getId(), path, content);
                    batch.addAll(chunks);

                    // Flush batch when it reaches the target size
                    if (batch.size() >= BATCH_SIZE) {
                        vectorStore.add(batch);
                        totalChunks += batch.size();
                        log.debug("Flushed batch of {} chunks (total so far: {})", batch.size(), totalChunks);
                        batch.clear();
                    }

                } catch (Exception e) {
                    failedFiles++;
                    log.warn("Skipping file {} – {}", path, e.getMessage());
                }

                processedCount++;
                repo.setFilesProcessed(processedCount);

                // Persist progress to DB every 10 files so the UI can poll it
                if (processedCount % 10 == 0 || processedCount == eligibleFiles.size()) {
                    repoRepository.save(repo);
                }
            }

            // Flush any remaining chunks
            if (!batch.isEmpty()) {
                vectorStore.add(batch);
                totalChunks += batch.size();
                log.debug("Flushed final batch of {} chunks", batch.size());
                batch.clear();
            }

            // ── 4. Persist SUCCESS state ──────────────────────────────────────
            repo.setIndexStatus(IndexStatus.READY);
            repo.setIndexedAt(Instant.now());
            repo.setChunkCount(totalChunks);
            repo.setFilesProcessed(processedCount);
            repoRepository.save(repo);

            log.info("Successfully indexed '{}': {} files processed ({} failed), {} chunks stored.",
                    repo.getFullName(), processedCount, failedFiles, totalChunks);

        } catch (Exception e) {
            // ── Catch-all: always persist FAILED so the UI stops hanging ─────
            log.error("Indexing pipeline FAILED for repo '{}': {}", repo.getFullName(), e.getMessage(), e);
            try {
                repo.setIndexStatus(IndexStatus.FAILED);
                repo.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                repoRepository.save(repo);
            } catch (Exception saveEx) {
                // Last-resort logging – we must not swallow this
                log.error("CRITICAL: Could not persist FAILED status for repo {}: {}", repoId, saveEx.getMessage(), saveEx);
            }
        }
    }
}