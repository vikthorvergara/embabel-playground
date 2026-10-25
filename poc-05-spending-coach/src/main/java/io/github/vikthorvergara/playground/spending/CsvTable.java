package io.github.vikthorvergara.playground.spending;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class CsvTable {

    static final char[] CANDIDATES = {',', ';', '\t', '|'};
    static final int SNIFF_LINES = 50;
    static final int SAMPLE_ROWS = 200;

    public record Decoded(String text, String charset) {
    }

    public record Row(int line, List<String> cells) {
    }

    private final char delimiter;
    private final List<String> header;
    private final int headerLine;
    private final List<Row> rows;

    private CsvTable(char delimiter, List<String> header, int headerLine, List<Row> rows) {
        this.delimiter = delimiter;
        this.header = header;
        this.headerLine = headerLine;
        this.rows = rows;
    }

    public static Decoded decode(byte[] bytes) {
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            return new Decoded(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8), "UTF-8");
        }
        if (startsWith(bytes, 0xFF, 0xFE)) {
            return new Decoded(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE), "UTF-16LE");
        }
        if (startsWith(bytes, 0xFE, 0xFF)) {
            return new Decoded(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE), "UTF-16BE");
        }
        CharsetDecoder strict = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return new Decoded(strict.decode(ByteBuffer.wrap(bytes)).toString(), "UTF-8");
        } catch (CharacterCodingException e) {
            return new Decoded(new String(bytes, Charset.forName("windows-1252")), "windows-1252");
        }
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    public static CsvTable parse(String text) {
        String body = text.startsWith("﻿") ? text.substring(1) : text;
        char delimiter = sniff(body);
        int expected = modalCount(body, delimiter) + 1;
        List<Row> records = tokenise(body, delimiter);
        List<String> header = List.of();
        int headerLine = 0;
        List<Row> rows = new ArrayList<>();
        for (Row record : records) {
            if (header.isEmpty()) {
                if (record.cells().size() == expected) {
                    header = record.cells().stream().map(String::strip).toList();
                    headerLine = record.line();
                }
                continue;
            }
            if (record.cells().size() == header.size() && record.cells().stream().anyMatch(cell -> !cell.isBlank())) {
                rows.add(record);
            }
        }
        return new CsvTable(delimiter, header, headerLine, rows);
    }

    static char sniff(String body) {
        char best = ',';
        int bestMode = -1;
        for (char candidate : CANDIDATES) {
            int mode = modalCount(body, candidate);
            if (mode > bestMode) {
                best = candidate;
                bestMode = mode;
            }
        }
        return best;
    }

    private static int modalCount(String body, char delimiter) {
        Map<Integer, Integer> frequency = new HashMap<>();
        String[] lines = body.split("\r\n|\n|\r", -1);
        for (int i = 0; i < Math.min(SNIFF_LINES, lines.length); i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            frequency.merge(countOutsideQuotes(lines[i], delimiter), 1, Integer::sum);
        }
        int mode = 0;
        int modeFrequency = 0;
        for (Map.Entry<Integer, Integer> entry : frequency.entrySet()) {
            if (entry.getValue() > modeFrequency || (entry.getValue() == modeFrequency && entry.getKey() > mode)) {
                mode = entry.getKey();
                modeFrequency = entry.getValue();
            }
        }
        return mode;
    }

    private static int countOutsideQuotes(String line, char delimiter) {
        int count = 0;
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == delimiter && !quoted) {
                count++;
            }
        }
        return count;
    }

    static List<Row> tokenise(String body, char delimiter) {
        List<Row> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int recordLine = 1;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < body.length() && body.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    cell.append(c);
                }
                continue;
            }
            if (c == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (c == delimiter) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < body.length() && body.charAt(i + 1) == '\n') {
                    i++;
                }
                cells.add(cell.toString());
                cell.setLength(0);
                records.add(new Row(recordLine, List.copyOf(cells)));
                cells.clear();
                line++;
                recordLine = line;
            } else {
                cell.append(c);
            }
        }
        if (!cell.isEmpty() || !cells.isEmpty()) {
            cells.add(cell.toString());
            records.add(new Row(recordLine, List.copyOf(cells)));
        }
        return records;
    }

    public char delimiter() {
        return delimiter;
    }

    public List<String> header() {
        return header;
    }

    public int headerLine() {
        return headerLine;
    }

    public List<Row> rows() {
        return rows;
    }

    public List<List<String>> rows(int n) {
        return rows.stream().limit(n).map(Row::cells).toList();
    }

    public List<String> column(int index, int limit) {
        return rows.stream().limit(limit)
                .map(row -> row.cells().get(index).strip())
                .filter(cell -> !cell.isEmpty())
                .toList();
    }

    public String normalisedHeader() {
        return header.stream().map(cell -> cell.strip().toLowerCase(Locale.ROOT)).collect(Collectors.joining("|"));
    }

    public String fingerprint() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalisedHeader().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
