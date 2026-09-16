package io.github.vikthorvergara.playground.prreview;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiffParserTest {

    @Test
    void countsChangesPerFile() throws IOException {
        Diff diff = DiffParser.parse(sample());

        assertThat(diff.files()).containsExactly(
                new Diff.FileChange("src/main/java/com/example/UserService.java", 4, 2),
                new Diff.FileChange("src/test/java/com/example/UserServiceTest.java", 1, 0));
        assertThat(diff.summary()).isEqualTo("2 file(s), +5 -2");
    }

    @Test
    void rejectsTextThatIsNotADiff() {
        assertThatThrownBy(() -> DiffParser.parse("please review my code"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static String sample() throws IOException {
        try (InputStream in = DiffParserTest.class.getResourceAsStream("/diffs/null-check.diff")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
