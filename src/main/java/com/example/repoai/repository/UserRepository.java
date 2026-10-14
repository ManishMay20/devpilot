package com.example.repoai.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.repoai.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByGithubId(Long githubId);
}
