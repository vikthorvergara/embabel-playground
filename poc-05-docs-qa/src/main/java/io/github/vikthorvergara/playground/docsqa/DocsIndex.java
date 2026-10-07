package io.github.vikthorvergara.playground.docsqa;

import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.common.core.types.SimilarityResult;
import com.embabel.common.core.types.TextSimilaritySearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * In-memory Lucene index over the project's Markdown docs, built once at startup.
 * Keyword (BM25) search only: no embedding model needed.
 */
@Component
public class DocsIndex {

    private static final Logger log = LoggerFactory.getLogger(DocsIndex.class);

    private final LuceneSearchOperations search;
    private final Map<String, String> sources = new TreeMap<>();
    private final int topK;
    private int chunkCount;

    public DocsIndex(DocsProperties properties) {
        this.topK = properties.topK();
        this.search = LuceneSearchOperations.withName("docs").build();
        Path root = properties.root().toAbsolutePath().normalize();
        for (String path : properties.paths()) {
            markdownFiles(root.resolve(path)).forEach(file -> index(root, file));
        }
        log.info("Indexed {} Markdown file(s) into {} chunk(s) from {}", sources.size(), chunkCount, root);
    }

    public LuceneSearchOperations searchOperations() {
        return search;
    }

    public List<SimilarityResult<Chunk>> search(String query) {
        return search.textSearch(TextSimilaritySearchRequest.create(query, 0.0, topK), Chunk.class);
    }

    public Map<String, String> sources() {
        return Map.copyOf(sources);
    }

    public Optional<String> source(String path) {
        return Optional.ofNullable(sources.get(path));
    }

    public int chunkCount() {
        return chunkCount;
    }

    private void index(Path root, Path file) {
        String relative = root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        try {
            String markdown = Files.readString(file);
            sources.put(relative, markdown);
            chunkCount += search.writeAndChunkDocument(MarkdownDocuments.parse(relative, markdown)).size();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private static List<Path> markdownFiles(Path path) {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        if (!Files.isDirectory(path)) {
            log.warn("Skipping {}: not found", path);
            return List.of();
        }
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
