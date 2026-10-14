package com.example.repoai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repositories", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "github_repo_id" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "default_branch")
    private String defaultBranch;

    private String language;

    @Column(name = "html_url")
    private String htmlUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_status", nullable = false)
    @Builder.Default
    private IndexStatus indexStatus = IndexStatus.NOT_INDEXED;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @Column(name = "chunk_count")
    @Builder.Default
    private Integer chunkCount = 0;

    @Column(name = "files_total")
    @Builder.Default
    private Integer filesTotal = 0;

    @Column(name = "files_processed")
    @Builder.Default
    private Integer filesProcessed = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getOwner() {
        if (fullName != null && fullName.contains("/")) {
            return fullName.split("/")[0];
        }
        return name;
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}