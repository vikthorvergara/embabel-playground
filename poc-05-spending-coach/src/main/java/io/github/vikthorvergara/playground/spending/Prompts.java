package io.github.vikthorvergara.playground.spending;

import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Prompts {

    private Prompts() {
    }

    public static String columnMapping(List<String> header, List<List<String>> sample) {
        String columns = IntStream.range(0, header.size())
                .mapToObj(i -> i + "=" + header.get(i))
                .collect(Collectors.joining(" | "));
        String rows = sample.stream().map(row -> "Row: " + String.join(" | ", row)).collect(Collectors.joining("\n"));
        return """
                This is the header of a bank or credit card CSV export, followed by a few rows. Amount digits are
                masked as 9 and text cells are shortened; the signs and separators are real. Columns are numbered from 0.

                Header: %s
                %s

                Decide which column holds the transaction date, which holds the description, and how the amount is
                given: one signed amount column, separate debit and credit columns, or an amount column with a
                debit/credit marker column. Say whether a positive amount means money spent. Name a status column and
                the values that mean a row is still pending if there is one, and a currency column if there is one.
                Use null for anything the file does not have.
                """.formatted(columns, rows);
    }

    public static String categorise(List<RuledLedger.Merchant> batch) {
        String taxonomy = java.util.Arrays.stream(Category.values())
                .filter(category -> category != Category.UNCATEGORISED)
                .map(category -> "- " + category + ": " + category.definition())
                .collect(Collectors.joining("\n"));
        String merchants = batch.stream()
                .map(merchant -> "m%d: %s (typical %s, seen %d times)".formatted(merchant.merchantId(), Pii.mask(merchant.name()),
                        merchant.typicalAmount().toPlainString(), merchant.occurrences()))
                .collect(Collectors.joining("\n"));
        return """
                Assign one category to each merchant below. The names come from card and bank statements, upper case,
                with numbers removed. Use exactly these categories:

                %s

                Merchants:
                %s

                Answer for every merchant id. Use OTHER with a low confidence when the name tells you nothing.
                """.formatted(taxonomy, merchants);
    }

    public static String coach(List<YearMonth> months) {
        return """
                Write a short coaching note for a person who has shared their bank and card statements covering
                these months: %s.

                Every figure must come from the tools: start with months and cashFlow, then spendByCategory,
                monthOverMonth, recurringCharges, anomalies, topMerchants and budgetSuggestions. Quote amounts exactly
                as the tools return them, never rounded or estimated. Give a summary of three or four sentences, up to
                five insights and three concrete savings with the monthly amount each would free up.
                """.formatted(months.stream().map(YearMonth::toString).collect(Collectors.joining(", ")));
    }
}
