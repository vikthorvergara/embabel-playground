package io.github.vikthorvergara.playground.depadvisor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VersionsTest {

    @Test
    void classifiesUpgrades() {
        assertThat(Versions.classify("1.2.3", "1.2.4")).isEqualTo(UpgradeType.PATCH);
        assertThat(Versions.classify("1.2.3", "1.3.0")).isEqualTo(UpgradeType.MINOR);
        assertThat(Versions.classify("1.2.3", "2.0.0")).isEqualTo(UpgradeType.MAJOR);
        assertThat(Versions.classify("1.2.3", "1.2.3")).isEqualTo(UpgradeType.UP_TO_DATE);
        assertThat(Versions.classify("2.0.0", "1.9.9")).isEqualTo(UpgradeType.UP_TO_DATE);
        assertThat(Versions.classify("1.2", "1.2.1")).isEqualTo(UpgradeType.PATCH);
        assertThat(Versions.classify("1.2.3", null)).isEqualTo(UpgradeType.UNKNOWN);
        assertThat(Versions.classify("LATEST", "1.0.0")).isEqualTo(UpgradeType.UNKNOWN);
    }

    @Test
    void qualifiersOnReleasesAreIgnoredForClassification() {
        assertThat(Versions.classify("33.0.0-jre", "33.4.8-jre")).isEqualTo(UpgradeType.MINOR);
        assertThat(Versions.classify("5.3.39.RELEASE", "6.0.0.RELEASE")).isEqualTo(UpgradeType.MAJOR);
    }

    @Test
    void latestStableSkipsPreReleases() {
        assertThat(Versions.latestStable(List.of("1.0.0", "1.1.0", "2.0.0-M1", "2.0.0-RC1", "1.2.0-SNAPSHOT", "1.1.1")))
                .contains("1.1.1");
        assertThat(Versions.latestStable(List.of("1.9", "1.10", "1.2"))).contains("1.10");
        assertThat(Versions.latestStable(List.of("2.0.0-beta1"))).isEmpty();
    }

    @Test
    void latestStableKeepsTheFlavourOfTheCurrentVersion() {
        List<String> guava = List.of("33.0.0-jre", "33.0.0-android", "33.4.8-jre", "33.4.8-android", "33.5.0-android");
        assertThat(Versions.latestStable(guava, "33.0.0-jre")).contains("33.4.8-jre");
        assertThat(Versions.latestStable(guava, "33.0.0-android")).contains("33.5.0-android");
        assertThat(Versions.latestStable(List.of("1.0.0", "1.1.0"), "1.0.0")).contains("1.1.0");
        assertThat(Versions.flavour("5.3.39.RELEASE")).isEqualTo("release");
        assertThat(Versions.flavour("1.2.3")).isEmpty();
    }

    @Test
    void releaseSortsAfterItsPreReleases() {
        assertThat(Versions.compare("1.0.0", "1.0.0-RC1")).isPositive();
        assertThat(Versions.compare("1.0.0-RC1", "1.0.0")).isNegative();
    }
}
