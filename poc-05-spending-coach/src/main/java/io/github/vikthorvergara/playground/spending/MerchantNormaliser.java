package io.github.vikthorvergara.playground.spending;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MerchantNormaliser {

    static final int MAX_LENGTH = 40;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern PREFIXES = Pattern.compile(
            "^(COMPRA COM CARTAO|COMPRA CARTAO|COMPRA|PAGTO|PAG|DEBITO AUTOMATICO|DEB AUT|DEBIT CARD PURCHASE|CARD PURCHASE|PURCHASE|POS)\\b\\s*");
    private static final Pattern STORE_NUMBERS = Pattern.compile("\\b(LOJA|STORE|FILIAL|UNIDADE)\\s*\\d+|#\\s*\\d+");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern NOISE = Pattern.compile("[^A-Z ]+");
    private static final Pattern SPACES = Pattern.compile(" {2,}");

    private MerchantNormaliser() {
    }

    public static String plain(String text) {
        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toUpperCase(Locale.ROOT);
    }

    public static String normalise(String raw) {
        String s = plain(raw == null ? "" : raw).replace('*', ' ');
        s = PREFIXES.matcher(s.strip()).replaceFirst("");
        s = STORE_NUMBERS.matcher(s).replaceAll(" ");
        s = DIGITS.matcher(s).replaceAll(" ");
        s = NOISE.matcher(s).replaceAll(" ");
        s = SPACES.matcher(s).replaceAll(" ").strip();
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH).strip() : s;
    }
}
