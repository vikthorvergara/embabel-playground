package io.github.vikthorvergara.playground.docsqa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocsIndexTest {

    @Test
    void indexesFilesAndDirectoriesAndFindsChunksByKeyword(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("docs/pocs"));
        Files.writeString(root.resolve("README.md"), "# Readme\n\n## Build\nRun mvn verify.\n");
        Files.writeString(root.resolve("docs/pocs/loop.md"), "# Loops\n\n## Critic\nThe critic uses RepeatUntilAcceptable.\n");
        Files.writeString(root.resolve("docs/notes.txt"), "not markdown");

        var index = new DocsIndex(new DocsProperties(root, List.of("README.md", "docs", "missing"), 5));

        assertThat(index.sources()).containsOnlyKeys("README.md", "docs/pocs/loop.md");
        assertThat(index.chunkCount()).isGreaterThanOrEqualTo(2);
        assertThat(index.search("RepeatUntilAcceptable"))
                .first()
                .satisfies(result -> {
                    assertThat(result.getMatch().getUri()).isEqualTo("docs/pocs/loop.md");
                    assertThat(result.getMatch().getText()).contains("The critic uses RepeatUntilAcceptable.");
                });
    }

    @Test
    void indexesTheRealProjectDocs() {
        // Tests run from the module directory, so ".." is the repository root
        var index = new DocsIndex(new DocsProperties(Path.of(".."), List.of("README.md", "docs"), 5));

        assertThat(index.sources()).containsKeys("README.md", "docs/pocs/poc-03-pr-review-critic.md");
        assertThat(index.search("RepeatUntilAcceptable extra generation"))
                .extracting(result -> result.getMatch().getUri())
                .contains("docs/pocs/poc-03-pr-review-critic.md");
    }
}
