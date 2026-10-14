package com.example.repoai.security;

import com.example.repoai.entity.User;
import com.example.repoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // GitHub user attributes extract karna
        Long githubId = ((Number) attributes.get("id")).longValue();
        String username = (String) attributes.get("login");
        String name = (String) attributes.get("name");
        String avatarUrl = (String) attributes.get("avatar_url");

        // Access token extract karna (repos fetch karne ke liye use hoga)
        String accessToken = userRequest.getAccessToken().getTokenValue();
        String scopes = String.join(",", userRequest.getAccessToken().getScopes());

        // Database me Upsert (Create or Update)
        User user = userRepository.findByGithubId(githubId)
                .map(existingUser -> {
                    existingUser.setGithubUsername(username);
                    existingUser.setDisplayName(name != null ? name : username);
                    existingUser.setAvatarUrl(avatarUrl);
                    existingUser.setAccessToken(accessToken);
                    existingUser.setTokenScopes(scopes);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .githubId(githubId)
                            .githubUsername(username)
                            .displayName(name != null ? name : username)
                            .avatarUrl(avatarUrl)
                            .accessToken(accessToken)
                            .tokenScopes(scopes)
                            .build();
                    return userRepository.save(newUser);
                });

        return new AppUserPrincipal(user, attributes);
    }
}