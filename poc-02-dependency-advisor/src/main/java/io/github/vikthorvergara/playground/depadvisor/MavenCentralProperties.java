package io.github.vikthorvergara.playground.depadvisor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("advisor.maven-central")
public record MavenCentralProperties(
        @DefaultValue("https://repo.maven.apache.org/maven2") String baseUrl,
        @DefaultValue("5s") Duration timeout) {
}
