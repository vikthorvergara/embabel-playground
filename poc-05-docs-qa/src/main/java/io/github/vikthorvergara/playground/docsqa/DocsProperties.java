package io.github.vikthorvergara.playground.docsqa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.util.List;

/**
 * @param root  directory the paths are resolved against; citations are reported relative to it
 * @param paths Markdown files or directories (searched recursively) to index
 * @param topK  how many chunks a single search returns
 */
@ConfigurationProperties("docs-qa")
public record DocsProperties(
        @DefaultValue(".") Path root,
        @DefaultValue({"README.md", "docs"}) List<String> paths,
        @DefaultValue("6") int topK) {
}
