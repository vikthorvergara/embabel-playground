package io.github.vikthorvergara.playground.depadvisor;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyAdvisorAgentTest {

    private FakeMavenCentral central;
    private DependencyAdvisorAgent agent;

    @BeforeEach
    void setUp() throws Exception {
        central = new FakeMavenCentral()
                .versions("com.google.guava", "guava", "33.0.0-jre", "33.4.8-jre")
                .versions("org.junit.jupiter", "junit-jupiter", "5.10.0", "5.10.2", "6.0.0");
        agent = new DependencyAdvisorAgent(
                new MavenCentralClient(new MavenCentralProperties(central.baseUrl(), Duration.ofSeconds(2))));
    }

    @AfterEach
    void tearDown() {
        central.close();
    }

    @Test
    void readPomAcceptsXmlOrAPath(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");

        assertThat(agent.readPom(new UserInput("<project/>")).source()).isEqualTo("input");
        assertThat(agent.readPom(new UserInput(dir.toString())).xml()).isEqualTo("<project/>");
        assertThat(agent.readPomRequest(new PomRequest(dir.toString())).xml()).isEqualTo("<project/>");
    }

    @Test
    void resolveVersionsClassifiesEachDependency() {
        VersionMatrix matrix = agent.resolveVersions(new DependencyList("demo", List.of(
                new Dependency("com.google.guava", "guava", "33.0.0-jre"),
                new Dependency("org.junit.jupiter", "junit-jupiter", "5.10.0"),
                new Dependency("com.example", "private-lib", "1.0.0")), List.of()));

        assertThat(matrix.checks()).extracting(VersionCheck::upgradeType)
                .containsExactly(UpgradeType.MINOR, UpgradeType.MAJOR, UpgradeType.UNKNOWN);
        assertThat(agent.hasMajorUpgrades(matrix)).isTrue();
        assertThat(agent.noMajorUpgrades(matrix)).isFalse();
    }

    @Test
    void skipAssessmentReturnsNoNotes() {
        assertThat(agent.skipAssessment(new VersionMatrix("demo", List.of(), List.of())).notes()).isEmpty();
    }

    @Test
    void reportOrdersSafestUpgradesFirst() {
        VersionMatrix matrix = new VersionMatrix("demo", List.of(
                check("a", "b", "1.0.0", "1.1.0", UpgradeType.MINOR),
                check("c", "d", "1.0.0", "1.0.1", UpgradeType.PATCH),
                check("e", "f", "1.0.0", "1.0.0", UpgradeType.UP_TO_DATE),
                check("g", "h", "1.0.0", null, UpgradeType.UNKNOWN)), List.of("x:y"));

        UpgradeReport report = agent.writeReport(matrix, new BreakingChangeAssessment(List.of()));

        assertThat(report.recommendedOrder()).extracting(c -> c.dependency().coordinates())
                .containsExactly("c:d", "a:b");
        assertThat(report.unknown()).containsExactly("g:h");
        assertThat(report.skipped()).containsExactly("x:y");
        assertThat(report.isDetailed()).isFalse();
    }

    @Test
    void assessBreakingChangesOnlyMentionsMajorUpgradesAndOffersTools() {
        var context = FakeOperationContext.create();
        var expected = new BreakingChangeAssessment(List.of(
                new BreakingChangeAssessment.Note("org.junit.jupiter:junit-jupiter", "MEDIUM", "Check extensions.", null)));
        context.expectResponse(expected);
        VersionMatrix matrix = new VersionMatrix("demo", List.of(
                check("org.junit.jupiter", "junit-jupiter", "5.10.0", "6.0.0", UpgradeType.MAJOR),
                check("com.google.guava", "guava", "33.0.0-jre", "33.4.8-jre", UpgradeType.MINOR)), List.of());

        assertThat(agent.assessBreakingChanges(matrix, context)).isEqualTo(expected);

        var invocation = context.getLlmInvocations().getFirst();
        assertThat(invocation.getPrompt())
                .contains("org.junit.jupiter:junit-jupiter: 5.10.0 -> 6.0.0")
                .doesNotContain("guava");
        assertThat(invocation.getInteraction().getTools()).extracting(tool -> tool.getDefinition().getName())
                .contains("latestVersion", "projectUrl");
    }

    @Test
    void reportDropsNotesForDependenciesThatWereNotFlagged() {
        VersionMatrix matrix = new VersionMatrix("demo", List.of(
                check("org.junit.jupiter", "junit-jupiter", "5.10.0", "6.0.0", UpgradeType.MAJOR)), List.of());
        var assessment = new BreakingChangeAssessment(List.of(
                new BreakingChangeAssessment.Note("org.junit.jupiter:junit-jupiter", "MEDIUM", "Check extensions.", null),
                new BreakingChangeAssessment.Note("made.up:thing", "HIGH", "Invented.", null)));

        UpgradeReport report = agent.writeReport(matrix, assessment);

        assertThat(report.breakingChangeNotes()).extracting(BreakingChangeAssessment.Note::coordinates)
                .containsExactly("org.junit.jupiter:junit-jupiter");
        assertThat(report.toMarkdown())
                .contains("1. `org.junit.jupiter:junit-jupiter` 5.10.0 → 6.0.0 (MAJOR)")
                .contains("[MEDIUM risk] Check extensions.")
                .doesNotContain("made.up");
    }

    private static VersionCheck check(String g, String a, String current, String latest, UpgradeType type) {
        return new VersionCheck(new Dependency(g, a, current), latest, type);
    }
}
