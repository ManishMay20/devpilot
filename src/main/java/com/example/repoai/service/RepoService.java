package com.example.repoai.service;

import com.example.repoai.entity.IndexStatus;
import com.example.repoai.entity.RepositoryEntity;
import com.example.repoai.entity.User;
import com.example.repoai.repository.RepoRepository;
import com.example.repoai.service.github.GithubApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepoService {

    private final RepoRepository repoRepository;
    private final GithubApiClient githubApiClient;

    @Transactional
    public List<RepositoryEntity> syncAndListRepos(User user) {
        // 1. GitHub API se user ke repos mangwao
        List<Map<String, Object>> remoteRepos = githubApiClient.listUserRepos(user.getAccessToken());

        List<RepositoryEntity> syncedList = new ArrayList<>();

        // 2. Har remote repo ko DB me Upsert (insert ya update) karo
        for (Map<String, Object> remote : remoteRepos) {
            Long githubRepoId = ((Number) remote.get("id")).longValue();
            String name = (String) remote.get("name");
            String fullName = (String) remote.get("full_name");
            boolean isPrivate = Boolean.TRUE.equals(remote.get("private"));
            String defaultBranch = (String) remote.get("default_branch");
            String language = (String) remote.get("language");
            String htmlUrl = (String) remote.get("html_url");
            String description = (String) remote.get("description");

            RepositoryEntity repo = repoRepository
                    .findByUserIdAndGithubRepoId(user.getId(), githubRepoId)
                    .map(existing -> {
                        existing.setName(name);
                        existing.setFullName(fullName);
                        existing.setPrivate(isPrivate);
                        existing.setDefaultBranch(defaultBranch);
                        existing.setLanguage(language);
                        existing.setHtmlUrl(htmlUrl);
                        existing.setDescription(description);
                        return existing;
                    })
                    .orElseGet(() -> RepositoryEntity.builder()
                            .userId(user.getId())
                            .githubRepoId(githubRepoId)
                            .name(name)
                            .fullName(fullName)
                            .isPrivate(isPrivate)
                            .defaultBranch(defaultBranch)
                            .language(language)
                            .htmlUrl(htmlUrl)
                            .description(description)
                            .indexStatus(IndexStatus.NOT_INDEXED)
                            .build());

            syncedList.add(repoRepository.save(repo));
        }

        return repoRepository.findByUserIdOrderByFullNameAsc(user.getId());
    }

    @Transactional(readOnly = true)
    public List<RepositoryEntity> getUserRepos(UUID userId) {
        return repoRepository.findByUserIdOrderByFullNameAsc(userId);
    }
}