package io.github.vikthorvergara.playground.depadvisor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MavenCentralClientTest {

    private FakeMavenCentral central;
    private MavenCentralClient client;

    @BeforeEach
    void setUp() throws Exception {
        central = new FakeMavenCentral();
        client = new MavenCentralClient(new MavenCentralProperties(central.baseUrl(), Duration.ofSeconds(2)));
    }

    @AfterEach
    void tearDown() {
        central.close();
    }

    @Test
    void returnsLatestStableVersionAndCachesIt() {
        central.versions("com.google.guava", "guava", "32.1.0-jre", "33.4.8-jre", "34.0.0-rc1");

        assertThat(client.latestStableVersion("com.google.guava", "guava")).contains("33.4.8-jre");
        assertThat(client.latestStableVersion("com.google.guava", "guava")).contains("33.4.8-jre");
        assertThat(central.requestCount()).isEqualTo(1);
    }

    @Test
    void flavourAwareLookupSharesTheCache() {
        central.versions("com.google.guava", "guava", "33.0.0-jre", "33.4.8-jre", "33.5.0-android");

        assertThat(client.latestStableVersion("com.google.guava", "guava", "33.0.0-jre")).contains("33.4.8-jre");
        assertThat(client.latestStableVersion("com.google.guava", "guava")).contains("33.5.0-android");
        assertThat(central.requestCount()).isEqualTo(1);
    }

    @Test
    void missingArtifactIsEmptyNotAnError() {
        assertThat(client.latestStableVersion("com.example", "does-not-exist")).isEmpty();
    }

    @Test
    void unreachableRepositoryIsEmptyNotAnError() {
        var offline = new MavenCentralClient(new MavenCentralProperties("http://127.0.0.1:1", Duration.ofMillis(200)));

        assertThat(offline.latestStableVersion("com.google.guava", "guava")).isEmpty();
    }

    @Test
    void projectUrlPrefersScmUrl() {
        central.pom("org.junit", "junit-bom", "5.0.0", """
                <project>
                  <url>https://junit.org</url>
                  <scm>
                    <url>https://github.com/junit-team/junit5</url>
                  </scm>
                </project>
                """);

        assertThat(client.projectUrl("org.junit", "junit-bom", "5.0.0")).contains("https://github.com/junit-team/junit5");
    }
}
