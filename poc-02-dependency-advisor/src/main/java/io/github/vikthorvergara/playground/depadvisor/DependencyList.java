package io.github.vikthorvergara.playground.depadvisor;

import java.util.List;

/**
 * @param dependencies dependencies with an explicit, resolved version
 * @param skipped      coordinates we could not check (managed by a BOM/parent, or unresolved property)
 */
public record DependencyList(String projectName, List<Dependency> dependencies, List<String> skipped) {
}
