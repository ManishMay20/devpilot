package com.example.repoai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class ReadRepoController {

    @GetMapping("/readrepo")
    public String readRepo() {
        System.out.println("Read Repo called");
        return "Read Repo called";
    }

}