package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTableTest {

    @Test
    void decodesTheThreeByteOrderMarks() {
        assertThat(CsvTable.decode(withPrefix(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "a,b".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo(new CsvTable.Decoded("a,b", "UTF-8"));
        assertThat(CsvTable.decode(withPrefix(new byte[]{(byte) 0xFF, (byte) 0xFE}, "a,b".getBytes(StandardCharsets.UTF_16LE))))
                .isEqualTo(new CsvTable.Decoded("a,b", "UTF-16LE"));
        assertThat(CsvTable.decode(withPrefix(new byte[]{(byte) 0xFE, (byte) 0xFF}, "a,b".getBytes(StandardCharsets.UTF_16BE))))
                .isEqualTo(new CsvTable.Decoded("a,b", "UTF-16BE"));
        assertThat(CsvTable.decode("ação".getBytes(StandardCharsets.UTF_8))).isEqualTo(new CsvTable.Decoded("ação", "UTF-8"));
    }

    @Test
    void invalidUtf8FallsBackToWindows1252() {
        CsvTable.Decoded decoded = CsvTable.decode(Fixtures.bytes("bank-q3.csv"));

        assertThat(decoded.charset()).isEqualTo("windows-1252");
        assertThat(decoded.text()).contains("Histórico").contains("MANUTENÇÃO");
    }

    @Test
    void sniffsTheModalDelimiter() {
        assertThat(Fixtures.table("bank-q3.csv").delimiter()).isEqualTo(';');
        assertThat(Fixtures.table("card-q3.csv").delimiter()).isEqualTo(',');
        assertThat(CsvTable.parse("a\tb\tc\n1\t2\t3\n4\t5\t6\n").delimiter()).isEqualTo('\t');
        assertThat(CsvTable.parse("a|b\n1,5|2\n3|4\n").delimiter()).isEqualTo('|');
    }

    @Test
    void headerIsTheFirstLineWithTheModalCountAndThePreambleIsDropped() {
        CsvTable table = Fixtures.table("bank-q3.csv");

        assertThat(table.header()).containsExactly("Data", "Histórico", "Débito", "Crédito", "Saldo");
        assertThat(table.headerLine()).isEqualTo(4);
        assertThat(table.rows()).hasSize(19);
        assertThat(table.rows().getFirst().line()).isEqualTo(5);
        assertThat(table.rows().getFirst().cells()).containsExactly("02/07/2026", "SUPERMERCADO BOM PREÇO", "312,45", "", "7.808,00");
    }

    @Test
    void parsesQuotedFieldsWithEmbeddedDelimiterAndNewline() {
        CsvTable table = CsvTable.parse("id,note,amount\r\n1,\"a,b\",2\r\n2,\"line1\nline2\",\"say \"\"hi\"\"\"\r\n");

        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(0).cells()).containsExactly("1", "a,b", "2");
        assertThat(table.rows().get(1).cells()).containsExactly("2", "line1\nline2", "say \"hi\"");
        assertThat(table.rows().get(1).line()).isEqualTo(3);
    }

    @Test
    void fingerprintDependsOnTheNormalisedHeaderOnly() {
        CsvTable a = CsvTable.parse("Date, Amount\n2026-07-01,1\n");
        CsvTable b = CsvTable.parse("date,amount\n2026-08-01,2\n");
        CsvTable c = CsvTable.parse("date,value\n2026-08-01,2\n");

        assertThat(a.fingerprint()).hasSize(8).isEqualTo(b.fingerprint()).isNotEqualTo(c.fingerprint());
        assertThat(a.column(1, 200)).isEqualTo(List.of("1"));
    }

    private static byte[] withPrefix(byte[] prefix, byte[] body) {
        byte[] all = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, all, 0, prefix.length);
        System.arraycopy(body, 0, all, prefix.length, body.length);
        return all;
    }
}
