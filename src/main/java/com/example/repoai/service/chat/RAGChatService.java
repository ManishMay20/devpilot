package com.example.repoai.service.chat;

import com.example.repoai.dto.ChatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RAG chat service.
 *
 * <p>
 * Two modes of operation:
 * </p>
 * <ol>
 * <li>{@link #chatSync} – synchronous (legacy) endpoint, returns full answer at
 * once.</li>
 * <li>{@link #chatStream} – reactive SSE stream; tokens are pushed to the
 * client as
 * they are generated, eliminating perceived latency.</li>
 * </ol>
 *
 * <h3>Performance notes</h3>
 * <ul>
 * <li><b>topK = 3</b> – keeps the context window concise; retrieving 4-5 chunks
 * rarely improves quality but measurably slows down Ollama.</li>
 * <li><b>similarityThreshold = 0.45</b> – rejects low-relevance chunks early
 * so the model is not distracted by noise.</li>
 * <li>The {@link VectorStore} query is filtered by {@code repo_id} so answers
 * are
 * always scoped to the selected repository.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGChatService {

    /** Number of most-relevant chunks to retrieve from PGVector. */
    private static final int TOP_K = 3;

    /** Discard chunks below this cosine-similarity score. */
    private static final double SIMILARITY_THRESHOLD = 0.45;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    // ─────────────────────────────────────────────────────────────────────────
    // Streaming (SSE) – preferred path
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Streams LLM tokens as a {@link Flux}{@code <String>} suitable for
     * Server-Sent Events.
     *
     * <p>
     * The method is intentionally non-blocking: vector retrieval is synchronous
     * (PGVector query is fast), then the LLM call is handed off to the reactive
     * Ollama client which emits tokens as they arrive.
     * </p>
     *
     * @param repoId       the repository whose indexed chunks to search
     * @param userQuestion the user's natural-language question
     * @return a cold {@link Flux} of token strings
     */
    public Flux<String> chatStream(UUID repoId, String userQuestion) {
        RetrievalResult result = retrieve(repoId, userQuestion);
        log.info("Stream chat – found {} relevant chunks for repo {}", result.docs().size(), repoId);

        String systemPrompt = buildSystemPrompt(result.context());

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userQuestion)
                .stream()
                .content();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Synchronous (legacy keep-alive for backward compat)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Blocking RAG call – waits for the full answer before returning.
     * Kept for backward compatibility with the existing {@code POST /chat}
     * endpoint.
     */
    public ChatDto.ChatResponse chatSync(UUID repoId, String userQuestion) {
        RetrievalResult result = retrieve(repoId, userQuestion);
        log.info("Sync chat – found {} relevant chunks for repo {}", result.docs().size(), repoId);

        String systemPrompt = buildSystemPrompt(result.context());

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(userQuestion)
                .call()
                .content();

        return new ChatDto.ChatResponse(answer, result.citations());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared retrieval logic
    // ─────────────────────────────────────────────────────────────────────────

    private RetrievalResult retrieve(UUID repoId, String userQuestion) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(userQuestion)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .filterExpression(b.eq("repo_id", repoId.toString()).build())
                .build();

        List<Document> similarDocs = vectorStore.similaritySearch(searchRequest);

        StringBuilder contextBuilder = new StringBuilder();
        List<ChatDto.Citation> citations = new ArrayList<>();

        for (Document doc : similarDocs) {
            String filePath = (String) doc.getMetadata().getOrDefault("file_path", "unknown");
            Number chunkIdx = (Number) doc.getMetadata().getOrDefault("chunk_index", 0);

            contextBuilder.append("\n--- File: ").append(filePath).append(" ---\n");
            contextBuilder.append(doc.getText()).append("\n");

            citations.add(new ChatDto.Citation(filePath, chunkIdx.intValue()));
        }

        return new RetrievalResult(similarDocs, contextBuilder.toString(), citations);
    }

    private static String buildSystemPrompt(String codeContext) {
        return """
                You are DevPilot, an expert AI software engineer analyzing a codebase.
                Answer the user's question accurately, concisely, and professionally using ONLY the provided code snippets below.
                If the answer cannot be found in the provided code, state that clearly – never fabricate code.
                Always cite the exact file paths when explaining code.

                Formatting and Style Guidelines:
                1. Markdown: Format your response using clean, valid GitHub Flavored Markdown (GFM).
                2. No Raw HTML: Do NOT include literal HTML tags such as <br>, <p>, <div>, or <span>. Use standard Markdown blank lines and paragraph breaks instead.
                3. Structure: Provide well-organized explanations with clear Markdown headings (##, ###) and concise bulleted lists.
                4. Code Blocks:
                   - Place code examples into separate, standalone fenced code blocks with the exact language identifier (e.g. ```java, ```javascript, ```json, ```bash).
                   - Never mix conversational text, headers, or narrative commentary inside code fences.
                   - Never output UI labels like "Copy" or "Copy code" before or around code blocks.
                   - Never duplicate code blocks.
                   - For references to symbols, variables, classes, methods, or file paths within text, use inline code backticks (e.g. `SecurityConfig`, `auth()`).
                5. Tables: Use valid Markdown tables ONLY when comparing components, configurations, endpoints, or data flows where a tabular layout adds genuine clarity. Always include a proper header row and delimiter line (e.g. | Column 1 | Column 2 |). Never generate empty or malformed tables.

                CODE CONTEXT:
                """ + codeContext;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal record
    // ─────────────────────────────────────────────────────────────────────────

    private record RetrievalResult(
            List<Document> docs,
            String context,
            List<ChatDto.Citation> citations) {
    }
}