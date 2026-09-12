package io.github.vikthorvergara.playground.depadvisor;

import java.util.List;

public record VersionMatrix(String projectName, List<VersionCheck> checks, List<String> skipped) {

    public List<VersionCheck> outdated() {
        return checks.stream().filter(VersionCheck::isOutdated).toList();
    }

    public List<VersionCheck> ofType(UpgradeType type) {
        return checks.stream().filter(check -> check.upgradeType() == type).toList();
    }

    public boolean hasMajorUpgrades() {
        return !ofType(UpgradeType.MAJOR).isEmpty();
    }
}
