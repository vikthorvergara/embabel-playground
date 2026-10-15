package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void quotesOnlyWhatNeedsQuoting() {
        CsvWriter writer = new CsvWriter(CsvWriter.Format.COMMA_DOT);

        assertThat(writer.render(List.of(Arrays.asList("a,b", "say \"hi\"", "plain", null, "two\nlines"))))
                .isEqualTo("\"a,b\",\"say \"\"hi\"\"\",plain,,\"two\nlines\"\n");
    }

    @Test
    void formatSetsDelimiterAndDecimalSeparatorTogether() {
        List<List<Object>> rows = List.of(Arrays.asList("x", new BigDecimal("1234.50"), LocalDate.of(2026, 7, 1)));

        assertThat(new CsvWriter(CsvWriter.Format.COMMA_DOT).render(rows)).isEqualTo("x,1234.50,2026-07-01\n");
        assertThat(new CsvWriter(CsvWriter.Format.SEMICOLON_COMMA).render(rows)).isEqualTo("x;1234,50;2026-07-01\n");
    }

    @Test
    void writesABomAndRoundTripsThroughCsvTable(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("out.csv");
        List<List<Object>> rows = List.of(
                Arrays.asList("merchant", "amount", "note"),
                Arrays.asList("CAFÉ, CENTRO", new BigDecimal("18.50"), "line1\nline2"),
                Arrays.asList("PLAIN", new BigDecimal("2.00"), "say \"hi\""));

        new CsvWriter(CsvWriter.Format.SEMICOLON_COMMA).write(file, rows);
        byte[] bytes = Files.readAllBytes(file);
        CsvTable.Decoded decoded = CsvTable.decode(bytes);
        CsvTable table = CsvTable.parse(decoded.text());

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(decoded.charset()).isEqualTo("UTF-8");
        assertThat(table.delimiter()).isEqualTo(';');
        assertThat(table.header()).containsExactly("merchant", "amount", "note");
        assertThat(table.rows().get(0).cells()).containsExactly("CAFÉ, CENTRO", "18,50", "line1\nline2");
        assertThat(table.rows().get(1).cells()).containsExactly("PLAIN", "2,00", "say \"hi\"");
    }

    @Test
    void atomicWriteReplacesTheFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested").resolve("learned.csv");

        CsvWriter.writeAtomically(file, "a,b\n");
        CsvWriter.writeAtomically(file, "c,d\n");

        assertThat(Files.readString(file)).isEqualTo("c,d\n");
        assertThat(Files.list(file.getParent())).hasSize(1);
    }
}
