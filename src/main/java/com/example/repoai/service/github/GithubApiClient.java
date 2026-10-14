package com.example.repoai.service.github;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GithubApiClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final RestClient restClient;

    public GithubApiClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public List<Map<String, Object>> listUserRepos(String accessToken) {
        List<Map<String, Object>> allRepos = new ArrayList<>();
        int page = 1;

        while (page <= 5) {
            final int currentPage = page; // <-- Yeh add karein

            List<Map<String, Object>> pageRepos = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/repos")
                            .queryParam("sort", "updated")
                            .queryParam("per_page", 100)
                            .queryParam("page", currentPage) // <-- Yahan currentPage use karein
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });

            if (pageRepos == null || pageRepos.isEmpty()) {
                break;
            }
            allRepos.addAll(pageRepos);
            if (pageRepos.size() < 100) {
                break;
            }
            page++;
        }
        return allRepos;
    }

    // 1. Repo ki saari files ka recursive tree fetch karna
    public Map<String, Object> getRepoTree(String accessToken, String owner, String repo, String defaultBranch) {
        return restClient.get()
                .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", owner, repo, defaultBranch)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    // 2. Specific file ka content fetch karna (Base64 decode karke plain text
    // return karega)
    public String getFileContent(String accessToken, String owner, String repo, String path) {
        Map<String, Object> response = restClient.get()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        if (response != null && response.containsKey("content")) {
            String base64Content = ((String) response.get("content")).replaceAll("\\s", "");
            byte[] decoded = java.util.Base64.getDecoder().decode(base64Content);
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        }
        return "";
    }
}