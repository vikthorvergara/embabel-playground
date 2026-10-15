package io.github.vikthorvergara.playground.spending;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record StatementUpload(
        @JsonPropertyDescription("Names returned by POST /uploads, such as <uuid>/bank.csv; send an empty array when unused")
        List<String> sources,
        @JsonPropertyDescription("Statements pasted inline as CSV text; send an empty array when unused")
        List<InlineStatement> inline,
        @JsonPropertyDescription("True to add a coaching note written by the model")
        boolean coach) {

    public record InlineStatement(
            @JsonPropertyDescription("File name, used in the report")
            String name,
            @JsonPropertyDescription("The CSV content")
            String csv) {

        @Override
        public String toString() {
            return name;
        }
    }

    public StatementUpload {
        sources = sources == null ? List.of() : List.copyOf(sources);
        inline = inline == null ? List.of() : List.copyOf(inline);
    }

    @Override
    public String toString() {
        return "StatementUpload[sources=" + sources + " inline=" + inline + " coach=" + coach + "]";
    }
}
