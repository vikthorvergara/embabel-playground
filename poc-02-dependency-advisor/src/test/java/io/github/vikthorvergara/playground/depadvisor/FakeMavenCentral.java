package io.github.vikthorvergara.playground.depadvisor;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** Tiny in-process stand-in for a Maven repository. Unknown paths return 404. */
final class FakeMavenCentral implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final AtomicInteger requests = new AtomicInteger();

    FakeMavenCentral() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String body = responses.get(exchange.getRequestURI().getPath());
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : bytes.length);
            if (body != null) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    int requestCount() {
        return requests.get();
    }

    FakeMavenCentral versions(String groupId, String artifactId, String... versions) {
        String list = List.of(versions).stream()
                .map(v -> "      <version>" + v + "</version>")
                .collect(Collectors.joining("\n"));
        responses.put("/" + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml", """
                <metadata>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <versioning>
                    <versions>
                %s
                    </versions>
                  </versioning>
                </metadata>
                """.formatted(groupId, artifactId, list));
        return this;
    }

    FakeMavenCentral pom(String groupId, String artifactId, String version, String xml) {
        responses.put("/%s/%s/%s/%s-%s.pom".formatted(groupId.replace('.', '/'), artifactId, version, artifactId, version), xml);
        return this;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
