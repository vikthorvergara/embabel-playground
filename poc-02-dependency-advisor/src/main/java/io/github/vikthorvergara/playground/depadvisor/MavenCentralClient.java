package io.github.vikthorvergara.playground.depadvisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads maven-metadata.xml and POMs straight from the repository. Lookups never throw:
 * a failure is cached as "unknown" so one bad artifact does not fail the whole run.
 */
@Component
public class MavenCentralClient {

    private static final Logger log = LoggerFactory.getLogger(MavenCentralClient.class);
    private static final Pattern VERSION = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>");
    private static final Pattern SCM_URL = Pattern.compile("<scm>.*?<url>\\s*([^<\\s]+)\\s*</url>", Pattern.DOTALL);
    private static final Pattern PROJECT_URL = Pattern.compile("^\\s{0,4}<url>\\s*([^<\\s]+)\\s*</url>", Pattern.MULTILINE);

    private final RestClient http;
    private final Map<String, Optional<List<String>>> versionsCache = new ConcurrentHashMap<>();

    public MavenCentralClient(MavenCentralProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.http = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public Optional<String> latestStableVersion(String groupId, String artifactId) {
        return versionsOf(groupId, artifactId).flatMap(Versions::latestStable);
    }

    /** Same, but keeps the flavour of the current version (-jre stays -jre). */
    public Optional<String> latestStableVersion(String groupId, String artifactId, String currentVersion) {
        return versionsOf(groupId, artifactId).flatMap(versions -> Versions.latestStable(versions, currentVersion));
    }

    private Optional<List<String>> versionsOf(String groupId, String artifactId) {
        return versionsCache.computeIfAbsent(groupId + ":" + artifactId,
                key -> fetch(path(groupId, artifactId) + "/maven-metadata.xml").map(MavenCentralClient::versions));
    }

    /** Best effort: SCM or project URL from the artifact's POM, which is usually where release notes live. */
    public Optional<String> projectUrl(String groupId, String artifactId, String version) {
        return fetch("%s/%s/%s-%s.pom".formatted(path(groupId, artifactId), version, artifactId, version))
                .flatMap(pom -> firstGroup(SCM_URL.matcher(pom)).or(() -> firstGroup(PROJECT_URL.matcher(pom))));
    }

    private Optional<String> fetch(String path) {
        try {
            return Optional.ofNullable(http.get().uri(path).retrieve().body(String.class));
        } catch (Exception e) {
            log.warn("Lookup failed for {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private static String path(String groupId, String artifactId) {
        return "/" + groupId.replace('.', '/') + "/" + artifactId;
    }

    private static List<String> versions(String metadata) {
        return VERSION.matcher(metadata).results().map(r -> r.group(1)).toList();
    }

    private static Optional<String> firstGroup(Matcher matcher) {
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
