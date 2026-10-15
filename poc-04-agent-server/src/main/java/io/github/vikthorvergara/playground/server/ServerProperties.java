package io.github.vikthorvergara.playground.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @param apiKey when set, every request except health checks must send it in the X-API-Key header
 */
@ConfigurationProperties("agent-server")
public record ServerProperties(String apiKey, boolean showPlanning, Map<String, Double> budgets) {

    public ServerProperties {
        budgets = budgets == null ? Map.of() : Map.copyOf(budgets);
    }

    public boolean requiresApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
