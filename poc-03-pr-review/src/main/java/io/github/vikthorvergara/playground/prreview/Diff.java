package io.github.vikthorvergara.playground.prreview;

import java.util.List;

public record Diff(List<FileChange> files, String text) {

    public record FileChange(String path, int additions, int deletions) {
    }

    public int additions() {
        return files.stream().mapToInt(FileChange::additions).sum();
    }

    public int deletions() {
        return files.stream().mapToInt(FileChange::deletions).sum();
    }

    public String summary() {
        return "%d file(s), +%d -%d".formatted(files.size(), additions(), deletions());
    }
}
