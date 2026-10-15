package io.github.vikthorvergara.playground.spending;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

public final class CsvWriter {

    public enum Format {
        COMMA_DOT(',', '.'),
        SEMICOLON_COMMA(';', ',');

        private final char delimiter;
        private final char decimalSeparator;

        Format(char delimiter, char decimalSeparator) {
            this.delimiter = delimiter;
            this.decimalSeparator = decimalSeparator;
        }

        public char delimiter() {
            return delimiter;
        }

        public char decimalSeparator() {
            return decimalSeparator;
        }
    }

    public static final String BOM = "﻿";

    private final Format format;

    public CsvWriter(Format format) {
        this.format = format;
    }

    public String render(List<? extends List<?>> rows) {
        return rows.stream()
                .map(row -> row.stream().map(this::cell).collect(Collectors.joining(String.valueOf(format.delimiter()))))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    public String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal number) {
            return number.toPlainString().replace('.', format.decimalSeparator());
        }
        return quote(String.valueOf(value), format.delimiter());
    }

    static String quote(String text, char delimiter) {
        boolean risky = text.indexOf(delimiter) >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        return risky ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    public void write(Path path, List<? extends List<?>> rows) throws IOException {
        Files.writeString(path, BOM + render(rows), StandardCharsets.UTF_8);
    }

    public static void writeAtomically(Path path, String content) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName().toString(), ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
