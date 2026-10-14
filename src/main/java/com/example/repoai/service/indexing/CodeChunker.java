package com.example.repoai.service.indexing;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Splits a source-code file into overlapping text chunks suitable for
 * embedding and vector-store ingestion.
 *
 * <h3>Tuning rationale</h3>
 * <ul>
 *   <li><b>Chunk size 1 400 chars (~350 tokens)</b> – large enough to contain
 *       a full method or class definition, but small enough for Ollama's
 *       embedding window.  Keeps micro-chunk count low, which is the #1
 *       driver of Ollama round-trip latency.</li>
 *   <li><b>Overlap 150 chars</b> – ensures that a concept split across a
 *       boundary is still retrievable from either adjacent chunk.</li>
 * </ul>
 */
@Component
public class CodeChunker {

    private static final int CHUNK_SIZE_CHARS    = 1_400;
    private static final int CHUNK_OVERLAP_CHARS = 150;

    /**
     * Chunk {@code content} into overlapping windows and wrap each window in
     * a Spring AI {@link Document} with rich metadata for filtered vector search.
     *
     * @param repoId   internal repository UUID (stored as metadata for per-repo filtering)
     * @param filePath repository-relative file path
     * @param content  raw file text
     * @return list of documents ready to be added to the {@link org.springframework.ai.vectorstore.VectorStore}
     */
    public List<Document> chunkFile(UUID repoId, String filePath, String content) {
        List<Document> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }

        int length     = content.length();
        int start      = 0;
        int chunkIndex = 0;

        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE_CHARS, length);

            // Try to break on a newline boundary to avoid splitting mid-line
            if (end < length) {
                int newlinePos = content.lastIndexOf('\n', end);
                if (newlinePos > start + (CHUNK_SIZE_CHARS / 2)) {
                    end = newlinePos + 1; // include the newline
                }
            }

            String chunkText = content.substring(start, end);

            Map<String, Object> metadata = Map.of(
                    "repo_id",     repoId.toString(),
                    "file_path",   filePath,
                    "chunk_index", chunkIndex);

            chunks.add(new Document(chunkText, metadata));

            if (end >= length) break;

            start = end - CHUNK_OVERLAP_CHARS;
            if (start < 0) start = 0;
            chunkIndex++;
        }

        return chunks;
    }
}