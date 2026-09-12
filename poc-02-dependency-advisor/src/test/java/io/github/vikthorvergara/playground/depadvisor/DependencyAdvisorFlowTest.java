package io.github.vikthorvergara.playground.depadvisor;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Runs the real planner end to end. Only the LLM is mocked; Maven Central is a local stub.
 */
@TestPropertySource(properties = {
        "ANTHROPIC_API_KEY=test-key",
        "embabel.agent.shell.interactive.enabled=false"
})
class DependencyAdvisorFlowTest extends EmbabelMockitoIntegrationTest {

    private static FakeMavenCentral central;

    @BeforeAll
    static void startRepository() throws IOException {
        central = new FakeMavenCentral()
                .versions("com.google.guava", "guava", "33.0.0-jre", "33.4.8-jre")
                .versions("org.junit.jupiter", "junit-jupiter", "5.10.0", "5.10.2", "6.0.0");
    }

    @AfterAll
    static void stopRepository() {
        central.close();
    }

    @DynamicPropertySource
    static void repositoryUrl(DynamicPropertyRegistry registry) {
        registry.add("advisor.maven-central.base-url", central::baseUrl);
    }

    @Test
    void minorUpgradesOnlyTakeTheQuickPathWithoutCallingTheLlm() {
        UpgradeReport report = run(pom("<dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>33.0.0-jre</version></dependency>"));

        assertThat(report.isDetailed()).isFalse();
        assertThat(report.recommendedOrder()).extracting(VersionCheck::latestVersion).containsExactly("33.4.8-jre");
        verify(llmOperations, never()).createObject(any(), any(), any(), any(), any());
    }

    @Test
    void majorUpgradeTakesTheDetailedPath() {
        whenCreateObject(prompt -> prompt.contains("major version upgrade"), BreakingChangeAssessment.class)
                .thenReturn(new BreakingChangeAssessment(List.of(new BreakingChangeAssessment.Note(
                        "org.junit.jupiter:junit-jupiter", "MEDIUM", "Extension API changes.", "https://junit.org"))));

        UpgradeReport report = run(pom("""
                <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>33.0.0-jre</version></dependency>
                <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.10.0</version></dependency>
                """));

        assertThat(report.isDetailed()).isTrue();
        assertThat(report.recommendedOrder()).extracting(check -> check.dependency().artifactId())
                .containsExactly("guava", "junit-jupiter");
        assertThat(report.breakingChangeNotes()).singleElement()
                .extracting(BreakingChangeAssessment.Note::summary).isEqualTo("Extension API changes.");
    }

    private UpgradeReport run(String pom) {
        return AgentInvocation.create(agentPlatform, UpgradeReport.class).invoke(new UserInput(pom));
    }

    private static String pom(String dependencies) {
        return "<project><artifactId>demo</artifactId><dependencies>" + dependencies + "</dependencies></project>";
    }
}
