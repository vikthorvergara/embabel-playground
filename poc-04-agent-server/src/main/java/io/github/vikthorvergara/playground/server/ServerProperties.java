package io.github.vikthorvergara.playground.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey when set, every request except health checks must send it in the X-API-Key header
 */
@ConfigurationProperties("agent-server")
public record ServerProperties(String apiKey) {

    public boolean requiresApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
