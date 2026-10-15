package io.github.vikthorvergara.playground.spending;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LayoutSniffer {

    static final double THRESHOLD = 0.8;
    static final List<String> DEFAULT_SKIP_STATUSES = List.of("PENDING", "PENDENTE", "PROCESSING", "EM PROCESSAMENTO");

    private static final Set<String> DATE = Set.of("date", "data", "posting date", "transaction date", "data lancamento",
            "data do lancamento", "data compra", "data da compra");
    private static final Set<String> POSTING = Set.of("posting date", "data lancamento", "data do lancamento");
    private static final Set<String> DESCRIPTION = Set.of("description", "descricao", "memo", "details", "merchant",
            "historico", "lancamento", "estabelecimento");
    private static final Set<String> AMOUNT = Set.of("amount", "valor", "value", "montante");
    private static final Set<String> DEBIT = Set.of("debit", "debito", "saida");
    private static final Set<String> CREDIT = Set.of("credit", "credito", "entrada");
    private static final Set<String> STATUS = Set.of("status", "situacao");
    private static final Set<String> CURRENCY = Set.of("currency", "moeda");
    private static final Set<String> CARD_HEADERS = Set.of("estabelecimento", "data compra", "data da compra");
    private static final List<String> CARD_NAMES = List.of("card", "cartao", "fatura", "credit");

    private final List<Layout> configured;
    private final Map<String, Layout> learned;

    public LayoutSniffer(List<Layout> configured, Map<String, Layout> learned) {
        this.configured = configured;
        this.learned = learned;
    }

    public static boolean looksLikeStatement(CsvTable table) {
        int dateColumn = -1;
        for (int column = 0; column < table.header().size(); column++) {
            if (share(table.column(column, CsvTable.SAMPLE_ROWS), Dates::isDate) >= THRESHOLD) {
                dateColumn = column;
                break;
            }
        }
        if (dateColumn < 0) {
            return false;
        }
        for (int column = 0; column < table.header().size(); column++) {
            if (column != dateColumn && share(table.column(column, CsvTable.SAMPLE_ROWS), Amounts::isAmount) >= THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private static double share(List<String> cells, java.util.function.Predicate<String> test) {
        if (cells.isEmpty()) {
            return 0;
        }
        return cells.stream().filter(test).count() / (double) cells.size();
    }

    public Optional<Layout> sniff(String name, CsvTable table, String fingerprint) {
        Optional<Layout> bySynonyms = fromSynonyms(name, table);
        if (bySynonyms.isPresent()) {
            return bySynonyms;
        }
        Optional<Layout> byConfiguration = fromConfigured(name, table);
        if (byConfiguration.isPresent()) {
            return byConfiguration;
        }
        Layout learnt = learned.get(fingerprint);
        if (learnt != null && learnt.width() <= table.header().size()) {
            return Optional.of(learnt.withName(name).withAmounts(amountsOf(table, learnt)));
        }
        return Optional.empty();
    }

    static Optional<Layout> fromSynonyms(String name, CsvTable table) {
        List<String> header = table.header().stream().map(LayoutSniffer::normaliseHeader).toList();
        List<Integer> dates = indexes(header, DATE);
        int description = first(header, DESCRIPTION);
        int amount = first(header, AMOUNT);
        int debit = first(header, DEBIT);
        int credit = first(header, CREDIT);
        if (dates.isEmpty() || description < 0 || (amount < 0 && (debit < 0 || credit < 0))) {
            return Optional.empty();
        }
        Integer posting = null;
        int date = dates.getFirst();
        if (dates.size() > 1) {
            for (int index : dates) {
                if (POSTING.contains(header.get(index)) && posting == null) {
                    posting = index;
                } else if (date == dates.getFirst() && posting != null && index != posting) {
                    date = index;
                }
            }
            if (posting != null && date == posting) {
                date = dates.stream().filter(index -> !index.equals(dates.getFirst())).findFirst().orElse(date);
            }
        }
        int status = first(header, STATUS);
        int currency = first(header, CURRENCY);
        Layout.Kind kind = kindOf(name, header);
        Layout layout = new Layout(name, kind, table.delimiter(), date, null, posting, description,
                amount < 0 ? null : amount, debit < 0 ? null : debit, credit < 0 ? null : credit, null, null,
                status < 0 ? null : status, status < 0 ? List.of() : DEFAULT_SKIP_STATUSES,
                currency < 0 ? null : currency, kind == Layout.Kind.CARD, null);
        return Optional.of(layout.withAmounts(amountsOf(table, layout)));
    }

    Optional<Layout> fromConfigured(String name, CsvTable table) {
        for (Layout candidate : configured) {
            if (candidate.delimiter() != table.delimiter() || candidate.width() > table.header().size()) {
                continue;
            }
            Layout layout = candidate.withName(name).withAmounts(amountsOf(table, candidate));
            if (StatementParser.parses(table.rows(5), layout)) {
                return Optional.of(layout);
            }
        }
        return Optional.empty();
    }

    static Layout.Kind kindOf(String name, List<String> normalisedHeader) {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean card = CARD_NAMES.stream().anyMatch(lower::contains) || normalisedHeader.stream().anyMatch(CARD_HEADERS::contains);
        return card ? Layout.Kind.CARD : Layout.Kind.BANK;
    }

    static Layout.AmountFormat amountsOf(CsvTable table, Layout layout) {
        List<String> cells = new ArrayList<>();
        for (Integer column : new Integer[]{layout.amountColumn(), layout.debitColumn(), layout.creditColumn()}) {
            if (column != null && column < table.header().size()) {
                cells.addAll(table.column(column, CsvTable.SAMPLE_ROWS));
            }
        }
        return Amounts.formatOf(cells);
    }

    static String normaliseHeader(String cell) {
        return MerchantNormaliser.plain(cell).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").strip();
    }

    private static List<Integer> indexes(List<String> header, Set<String> synonyms) {
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            if (synonyms.contains(header.get(i))) {
                found.add(i);
            }
        }
        return found;
    }

    private static int first(List<String> header, Set<String> synonyms) {
        List<Integer> found = indexes(header, synonyms);
        return found.isEmpty() ? -1 : found.getFirst();
    }
}
