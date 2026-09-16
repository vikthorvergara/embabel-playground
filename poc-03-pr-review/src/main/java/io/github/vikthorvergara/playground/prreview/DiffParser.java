package io.github.vikthorvergara.playground.prreview;

import java.util.ArrayList;
import java.util.List;

final class DiffParser {

    private DiffParser() {
    }

    static Diff parse(String text) {
        List<Diff.FileChange> files = new ArrayList<>();
        String path = null;
        int additions = 0;
        int deletions = 0;
        for (String line : text.lines().toList()) {
            if (line.startsWith("diff --git ")) {
                if (path != null) {
                    files.add(new Diff.FileChange(path, additions, deletions));
                }
                path = line.substring(line.lastIndexOf(" b/") + 3);
                additions = 0;
                deletions = 0;
            } else if (line.startsWith("+++ ") || line.startsWith("--- ")) {
                if (line.startsWith("+++ b/") && path == null) {
                    path = line.substring(6);
                }
            } else if (line.startsWith("+")) {
                additions++;
            } else if (line.startsWith("-")) {
                deletions++;
            }
        }
        if (path != null) {
            files.add(new Diff.FileChange(path, additions, deletions));
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Input does not look like a unified diff");
        }
        return new Diff(List.copyOf(files), text);
    }
}
