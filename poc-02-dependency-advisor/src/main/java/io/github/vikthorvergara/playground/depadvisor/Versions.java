package io.github.vikthorvergara.playground.depadvisor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Just enough version handling for Maven Central artifacts. Compares the numeric
 * part only (major.minor.patch) and treats anything with a qualifier like
 * -M1, -RC2, -beta or -SNAPSHOT as a pre-release.
 */
final class Versions {

    private static final Pattern PRE_RELEASE =
            Pattern.compile("(?i).*[-.](alpha|beta|rc|cr|m\\d+|milestone|preview|snapshot|ea|dev)[-.\\d]*$");

    static final Comparator<String> COMPARATOR = Versions::compare;

    private Versions() {
    }

    static boolean isStable(String version) {
        return !PRE_RELEASE.matcher(version).matches() && !numbers(version).isEmpty();
    }

    static Optional<String> latestStable(Collection<String> versions) {
        return versions.stream().filter(Versions::isStable).max(COMPARATOR);
    }

    /**
     * Latest stable version with the same flavour as {@code current}: Guava publishes both
     * 33.4.8-jre and 33.4.8-android, and a -jre project should not be told to move to -android.
     */
    static Optional<String> latestStable(Collection<String> versions, String current) {
        String flavour = flavour(current);
        List<String> sameFlavour = versions.stream().filter(v -> flavour(v).equals(flavour)).toList();
        return latestStable(sameFlavour.isEmpty() ? versions : sameFlavour);
    }

    /** Whatever follows the numeric part: "jre" for 33.4.8-jre, "" for 1.2.3. */
    static String flavour(String version) {
        String[] tokens = version.split("[.-]");
        int i = 0;
        while (i < tokens.length && tokens[i].matches("\\d+")) {
            i++;
        }
        return String.join("-", Arrays.copyOfRange(tokens, i, tokens.length)).toLowerCase(Locale.ROOT);
    }

    static UpgradeType classify(String current, String latest) {
        if (latest == null) {
            return UpgradeType.UNKNOWN;
        }
        List<Integer> c = numbers(current);
        List<Integer> l = numbers(latest);
        if (c.isEmpty() || l.isEmpty()) {
            return UpgradeType.UNKNOWN;
        }
        if (compare(current, latest) >= 0) {
            return UpgradeType.UP_TO_DATE;
        }
        if (!part(c, 0).equals(part(l, 0))) {
            return UpgradeType.MAJOR;
        }
        if (!part(c, 1).equals(part(l, 1))) {
            return UpgradeType.MINOR;
        }
        return UpgradeType.PATCH;
    }

    static int compare(String a, String b) {
        List<Integer> x = numbers(a);
        List<Integer> y = numbers(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            int cmp = Integer.compare(part(x, i), part(y, i));
            if (cmp != 0) {
                return cmp;
            }
        }
        // Same numbers: a release sorts after its pre-releases (1.0.0 > 1.0.0-RC1)
        return Boolean.compare(isStableQualifier(a), isStableQualifier(b));
    }

    private static boolean isStableQualifier(String version) {
        return !PRE_RELEASE.matcher(version).matches();
    }

    private static Integer part(List<Integer> parts, int index) {
        return index < parts.size() ? parts.get(index) : 0;
    }

    private static List<Integer> numbers(String version) {
        List<Integer> parts = new ArrayList<>();
        for (String token : version.split("[.-]")) {
            if (!token.matches("\\d+")) {
                break;
            }
            parts.add(Integer.parseInt(token));
        }
        return parts;
    }
}
