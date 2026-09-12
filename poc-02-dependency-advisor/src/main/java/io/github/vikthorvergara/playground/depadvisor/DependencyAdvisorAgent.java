package io.github.vikthorvergara.playground.depadvisor;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Agent(description = "Checks the dependencies in a Maven pom.xml against Maven Central and recommends an upgrade order")
public class DependencyAdvisorAgent {

    static final String HAS_MAJOR_UPGRADES = "hasMajorUpgrades";
    static final String NO_MAJOR_UPGRADES = "noMajorUpgrades";

    private static final Comparator<VersionCheck> SAFEST_FIRST = Comparator
            .comparing(VersionCheck::upgradeType)
            .thenComparing(check -> check.dependency().coordinates());

    private final MavenCentralClient mavenCentral;

    public DependencyAdvisorAgent(MavenCentralClient mavenCentral) {
        this.mavenCentral = mavenCentral;
    }

    @Action(description = "Read a pom.xml, either pasted in directly or from a file path")
    public PomFile readPom(UserInput input) {
        String content = input.getContent().strip();
        if (content.startsWith("<")) {
            return new PomFile("input", content);
        }
        Path path = Path.of(content);
        if (Files.isDirectory(path)) {
            path = path.resolve("pom.xml");
        }
        try {
            return new PomFile(path.toString(), Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    @Action(description = "Parse dependencies with explicit versions out of the POM")
    public DependencyList parsePom(PomFile pom) {
        return PomParser.parse(pom);
    }

    // post tells the planner this action is what settles the two conditions below. Without it
    // the planner cannot find a path to either report and the process gets stuck.
    @Action(post = {HAS_MAJOR_UPGRADES, NO_MAJOR_UPGRADES},
            description = "Look up the latest stable version of each dependency on Maven Central")
    public VersionMatrix resolveVersions(DependencyList dependencies) {
        List<VersionCheck> checks = dependencies.dependencies().stream()
                .map(dep -> {
                    String latest = mavenCentral.latestStableVersion(dep.groupId(), dep.artifactId(), dep.version()).orElse(null);
                    return new VersionCheck(dep, latest, Versions.classify(dep.version(), latest));
                })
                .toList();
        return new VersionMatrix(dependencies.projectName(), checks, dependencies.skipped());
    }

    @Condition(name = HAS_MAJOR_UPGRADES)
    public boolean hasMajorUpgrades(VersionMatrix matrix) {
        return matrix.hasMajorUpgrades();
    }

    @Condition(name = NO_MAJOR_UPGRADES)
    public boolean noMajorUpgrades(VersionMatrix matrix) {
        return !matrix.hasMajorUpgrades();
    }

    @Action(pre = NO_MAJOR_UPGRADES, description = "Skip the risk assessment when only patch and minor upgrades are available")
    public BreakingChangeAssessment skipAssessment(VersionMatrix matrix) {
        return new BreakingChangeAssessment(List.of());
    }

    @Action(pre = HAS_MAJOR_UPGRADES, description = "Assess breaking change risk for major upgrades")
    public BreakingChangeAssessment assessBreakingChanges(VersionMatrix matrix, OperationContext context) {
        String majors = matrix.ofType(UpgradeType.MAJOR).stream()
                .map(c -> "- %s: %s -> %s".formatted(c.dependency().coordinates(), c.dependency().version(), c.latestVersion()))
                .collect(Collectors.joining("\n"));
        return context.ai()
                .withDefaultLlm()
                .withToolObject(new DependencyTools(mavenCentral))
                .createObject("""
                        These Maven dependencies have a major version upgrade available:

                        %s

                        For each one, write a short note on the breaking change risk: what usually changes between
                        these major versions and what the team should check. Use the projectUrl tool to find a
                        link to release notes or a migration guide. Do not state any version numbers other than the
                        ones above or ones returned by the tools. Return one note per dependency, using the exact
                        groupId:artifactId given above.
                        """.formatted(majors),
                        BreakingChangeAssessment.class);
    }

    @AchievesGoal(description = "Produced a dependency upgrade report for a Maven project")
    @Action(description = "Write the upgrade report, including breaking change notes for major upgrades")
    public UpgradeReport writeReport(VersionMatrix matrix, BreakingChangeAssessment assessment) {
        Set<String> majors = matrix.ofType(UpgradeType.MAJOR).stream()
                .map(c -> c.dependency().coordinates())
                .collect(Collectors.toSet());
        // Drop anything the LLM made up: only notes for dependencies we actually flagged survive
        List<BreakingChangeAssessment.Note> notes = assessment.notes().stream()
                .filter(note -> majors.contains(note.coordinates()))
                .toList();
        List<VersionCheck> order = matrix.outdated().stream().sorted(SAFEST_FIRST).toList();
        List<String> unknown = matrix.ofType(UpgradeType.UNKNOWN).stream()
                .map(c -> c.dependency().coordinates())
                .toList();
        return new UpgradeReport(matrix.projectName(), order, notes, unknown, matrix.skipped());
    }
}
