package com.example.repoai.repository;

import com.example.repoai.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepoRepository extends JpaRepository<RepositoryEntity, UUID> {
    List<RepositoryEntity> findByUserIdOrderByFullNameAsc(UUID userId);

    Optional<RepositoryEntity> findByUserIdAndGithubRepoId(UUID userId, Long githubRepoId);

    Optional<RepositoryEntity> findByIdAndUserId(UUID id, UUID userId);
}