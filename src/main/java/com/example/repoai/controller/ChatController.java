package com.example.repoai.controller;

import com.example.repoai.dto.ChatDto;
import com.example.repoai.security.AppUserPrincipal;
import com.example.repoai.service.chat.RAGChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Chat endpoints for the DevPilot RAG assistant.
 *
 * <ul>
 *   <li>{@code POST /{repoId}/chat} – synchronous (legacy). Returns the full answer once
 *       the LLM has finished generating.</li>
 *   <li>{@code GET  /{repoId}/chat/stream?message=…} – SSE streaming. Tokens arrive in
 *       real time so the UI can render progressively without waiting for the full response.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class ChatController {

    private final RAGChatService chatService;

    // ─────────────────────────────────────────────────────────────────────────
    //  Streaming SSE endpoint (preferred – use this in the UI)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Server-Sent Events streaming chat.
     *
     * <p>Each SSE event carries a single token string as plain text.  The client
     * should accumulate tokens until the stream closes, then consider the message
     * complete.</p>
     *
     * <p>Produces {@code text/event-stream} so browsers and {@code EventSource}
     * objects handle reconnection automatically.</p>
     */
    @GetMapping(
            value  = "/{repoId}/chat/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @PathVariable UUID repoId,
            @RequestParam("message") String message,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        if (principal == null) {
            return Flux.error(new SecurityException("Unauthorized"));
        }

        if (message == null || message.isBlank()) {
            return Flux.error(new IllegalArgumentException("message must not be blank"));
        }

        log.debug("SSE stream request – repoId={}, message.length={}", repoId, message.length());

        return chatService.chatStream(repoId, message.trim());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Synchronous endpoint (kept for backward compatibility)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Blocking chat – waits for the full LLM response before returning.
     * Retained so existing integrations do not break while the UI migrates
     * to the streaming endpoint.
     */
    @PostMapping("/{repoId}/chat")
    public ResponseEntity<ChatDto.ChatResponse> askQuestion(
            @PathVariable UUID repoId,
            @RequestBody ChatDto.ChatRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        ChatDto.ChatResponse response = chatService.chatSync(repoId, request.getMessage());
        return ResponseEntity.ok(response);
    }
}