package io.github.vikthorvergara.playground.depadvisor;

import com.embabel.agent.api.annotation.LlmTool;

/**
 * Tools the LLM can call while assessing major upgrades. Every version it reports
 * has to come from here, not from its training data.
 */
public class DependencyTools {

    static final String NOT_FOUND = "unknown";

    private final MavenCentralClient mavenCentral;

    public DependencyTools(MavenCentralClient mavenCentral) {
        this.mavenCentral = mavenCentral;
    }

    @LlmTool(description = "Latest stable version of a Maven artifact on Maven Central")
    public String latestVersion(
            @LlmTool.Param(description = "Maven groupId, e.g. org.springframework.boot") String groupId,
            @LlmTool.Param(description = "Maven artifactId, e.g. spring-boot-starter") String artifactId) {
        return mavenCentral.latestStableVersion(groupId, artifactId).orElse(NOT_FOUND);
    }

    @LlmTool(description = "Project or source repository URL for a specific version of a Maven artifact. "
            + "Release notes and migration guides are usually linked from there.")
    public String projectUrl(
            @LlmTool.Param(description = "Maven groupId") String groupId,
            @LlmTool.Param(description = "Maven artifactId") String artifactId,
            @LlmTool.Param(description = "Version to look up") String version) {
        return mavenCentral.projectUrl(groupId, artifactId, version).orElse(NOT_FOUND);
    }
}
