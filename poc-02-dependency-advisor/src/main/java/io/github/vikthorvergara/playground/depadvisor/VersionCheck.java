package io.github.vikthorvergara.playground.depadvisor;

public record VersionCheck(Dependency dependency, String latestVersion, UpgradeType upgradeType) {

    public boolean isOutdated() {
        return switch (upgradeType) {
            case PATCH, MINOR, MAJOR -> true;
            case UP_TO_DATE, UNKNOWN -> false;
        };
    }
}
