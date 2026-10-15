package io.github.vikthorvergara.playground.server;

import io.github.vikthorvergara.playground.spending.ReportWriter;
import io.github.vikthorvergara.playground.spending.SpendingProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Statements go in as multipart files and come back out as the report files the spending agent wrote,
 * so the CSV bytes never travel through a JSON body or an MCP client's context.
 */
@RestController
class UploadController {

    static final Pattern RUN_ID = Pattern.compile("[A-Za-z0-9-]+");
    static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv");
    static final MediaType TEXT_MARKDOWN = MediaType.parseMediaType("text/markdown");
    static final String DEFAULT_NAME = "statement.csv";

    private final SpendingProperties properties;

    UploadController(SpendingProperties properties) {
        this.properties = properties;
    }

    record UploadResponse(List<String> sources) {
    }

    @PostMapping("/uploads")
    UploadResponse upload(@RequestParam("files") List<MultipartFile> files) throws IOException {
        String id = UUID.randomUUID().toString();
        Path dir = properties.uploads().resolve(id);
        Files.createDirectories(dir);
        List<String> sources = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = sanitise(file.getOriginalFilename());
            Files.write(dir.resolve(name), file.getBytes());
            sources.add(id + "/" + name);
        }
        return new UploadResponse(sources);
    }

    @GetMapping("/reports/{runId}/{file}")
    ResponseEntity<Resource> report(@PathVariable("runId") String runId, @PathVariable("file") String file) {
        if (!RUN_ID.matcher(runId).matches() || !ReportWriter.FILES.contains(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such report file");
        }
        Path path = properties.reports().resolve(runId).resolve(file);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such report file");
        }
        return ResponseEntity.ok()
                .contentType(file.endsWith(".md") ? TEXT_MARKDOWN : TEXT_CSV)
                .body(new FileSystemResource(path));
    }

    static String sanitise(String originalName) {
        String name = originalName == null ? "" : originalName;
        int cut = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = cut >= 0 ? name.substring(cut + 1) : name;
        String safe = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^\\.+", "");
        return safe.isBlank() ? DEFAULT_NAME : safe;
    }
}
