package com.example.repoai.service.indexing;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Decides which repository files are worth embedding.
 *
 * Strategy: only include core source-code files (the ones that carry
 * semantic meaning for a RAG assistant).  Everything else – lock files,
 * images, CSS, JSON data blobs, map files, etc. – is noise that inflates
 * the chunk count and slows down Ollama without improving answer quality.
 */
@Component
public class CodeFileFilter {

    // Directories we never descend into
    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", "target", "build", "dist", ".next",
            ".idea", ".vscode", "coverage", "__pycache__", "vendor",
            ".gradle", "out", ".cache", "tmp", "temp", "logs");

    /**
     * Core source-code extensions.
     *
     * Intentionally excludes:
     *   - .md  – documentation, low signal for code questions
     *   - .css / .scss / .sass – styling, rarely relevant to logic questions
     *   - .json – usually config/data, not logic; lock files handled separately
     *   - .yml / .yaml – config files; too many false positives
     *   - .xml – config/markup
     *   - .html – markup, rarely the answer to a code question
     *   - .svg / .png / .jpg / .gif – binary assets
     *   - .map – source-map files, minified noise
     *   - .lock – dependency lock files (yarn.lock, package-lock.json, etc.)
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            // JVM
            "java", "kt", "kts", "scala", "groovy",
            // Python
            "py", "pyx",
            // JavaScript / TypeScript
            "js", "jsx", "mjs", "cjs",
            "ts", "tsx",
            // Go
            "go",
            // Rust
            "rs",
            // C / C++
            "c", "h", "cpp", "cc", "cxx", "hpp", "hxx",
            // C#
            "cs",
            // Ruby
            "rb",
            // PHP
            "php",
            // Swift / Objective-C
            "swift", "m", "mm",
            // SQL (schema / migrations are high-value)
            "sql",
            // Shell scripts
            "sh", "bash", "zsh",
            // Terraform / Pulumi IaC (HCL)
            "tf", "hcl");

    // Explicit file-name blocklist (regardless of extension)
    private static final Set<String> IGNORED_FILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            "cargo.lock", "poetry.lock", "Gemfile.lock",
            "composer.lock", "mix.lock",
            ".DS_Store", "Thumbs.db");

    /**
     * Returns {@code true} if the file at {@code path} should be indexed.
     *
     * @param path       repository-relative path (e.g. {@code "src/main/App.java"})
     * @param sizeBytes  file size in bytes (0 if unknown)
     */
    public boolean isEligible(String path, long sizeBytes) {
        // Skip very large files – they overwhelm the chunker and rarely
        // contain a single coherent unit of logic
        if (sizeBytes > 150_000) {
            return false;
        }

        String[] parts = path.split("/");

        // Reject if any path segment is a known noise directory
        for (String part : parts) {
            if (IGNORED_DIRS.contains(part)) {
                return false;
            }
        }

        String filename = parts[parts.length - 1];

        // Exact-name blocklist (e.g. lock files)
        if (IGNORED_FILES.contains(filename)) {
            return false;
        }

        // Special-case: Dockerfile has no extension but is high-value
        if (filename.equalsIgnoreCase("Dockerfile") || filename.equalsIgnoreCase("Makefile")) {
            return true;
        }

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) {
            return false; // no extension and not a special file → skip
        }

        String ext = filename.substring(dotIndex + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext);
    }
}