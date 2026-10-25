package io.github.vikthorvergara.playground.spending;

import java.util.regex.Pattern;

public final class Pii {

    static final Pattern DATE = Pattern.compile("\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{2}[./-]\\d{2}[./-]\\d{4})\\b");
    static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b");
    static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    static final Pattern DIGIT_GROUP = Pattern.compile("\\d(?:[ .-]?\\d){7,}");

    private Pii() {
    }

    public static boolean found(String text) {
        String withoutDates = DATE.matcher(text).replaceAll(" ");
        return IBAN.matcher(withoutDates).find()
                || EMAIL.matcher(withoutDates).find()
                || DIGIT_GROUP.matcher(withoutDates).find();
    }

    public static String mask(String text) {
        String masked = IBAN.matcher(text).replaceAll("#");
        masked = EMAIL.matcher(masked).replaceAll("#");
        return DIGIT_GROUP.matcher(masked).replaceAll("#");
    }
}
