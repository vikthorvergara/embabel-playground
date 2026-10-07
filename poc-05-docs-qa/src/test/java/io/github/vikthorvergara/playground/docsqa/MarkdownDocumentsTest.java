package io.github.vikthorvergara.playground.docsqa;

import com.embabel.agent.rag.model.LeafSection;
import com.embabel.agent.rag.model.MaterializedDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownDocumentsTest {

    @Test
    void oneSectionPerHeadingIgnoringHashesInCodeFences() {
        MaterializedDocument doc = MarkdownDocuments.parse("docs/guide.md", """
                Intro before any heading.

                # Guide

                ## Install
                Run the build.

                ```bash
                # not a heading
                mvn verify
                ```

                ## Empty

                ### Usage ###
                Call the agent.
                """);

        assertThat(doc.getTitle()).isEqualTo("Guide");
        assertThat(doc.getUri()).isEqualTo("docs/guide.md");
        assertThat(doc.getMetadata()).containsEntry("url", "docs/guide.md");
        assertThat(doc.getChildren()).extracting(s -> s.getTitle())
                .containsExactly("docs/guide.md", "Install", "Usage");
        LeafSection install = (LeafSection) doc.getChildren().get(1);
        assertThat(install.getText()).startsWith("Install\n\nRun the build.").contains("# not a heading");
    }
}
