package com.example.repoai.controller;

import com.example.repoai.entity.User;
import com.example.repoai.security.AppUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    /**
     * Returns the currently authenticated user's profile info.
     * Called by the frontend on every page load to check auth status.
     * Returns 401 automatically if the user is not authenticated (Spring Security handles it).
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @AuthenticationPrincipal AppUserPrincipal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        User user = principal.getUser();

        Map<String, Object> body = Map.of(
                "id",              user.getId().toString(),
                "githubUsername",  user.getGithubUsername(),
                "displayName",     user.getDisplayName()  != null ? user.getDisplayName()  : user.getGithubUsername(),
                "avatarUrl",       user.getAvatarUrl()    != null ? user.getAvatarUrl()    : "",
                "createdAt",       user.getCreatedAt().toString()
        );

        return ResponseEntity.ok(body);
    }
}
