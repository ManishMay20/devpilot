package com.example.repoai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class ChatDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatRequest {
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class Citation {
        private String filePath;
        private int chunkIndex;
    }

    @Data
    @AllArgsConstructor
    public static class ChatResponse {
        private String answer;
        private List<Citation> citations;
    }
}