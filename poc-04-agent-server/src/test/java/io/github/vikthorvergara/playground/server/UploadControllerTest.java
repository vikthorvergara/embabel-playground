package io.github.vikthorvergara.playground.server;

import io.github.vikthorvergara.playground.spending.SpendingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadController.class)
@EnableConfigurationProperties({ServerProperties.class, SpendingProperties.class})
class UploadControllerTest {

    private static final Path WORK = createWorkDir();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void workDir(DynamicPropertyRegistry registry) {
        registry.add("spending.work-dir", WORK::toString);
    }

    @Test
    void savesAFileAboveOneMegabyteUnderAFreshUploadId() throws Exception {
        byte[] big = new byte[1_500_000];
        java.util.Arrays.fill(big, (byte) 'a');

        MvcResult result = mvc.perform(multipart("/uploads")
                        .file(new MockMultipartFile("files", "bank.csv", "text/csv", big))
                        .file(new MockMultipartFile("files", "card.csv", "text/csv", "date,amount\n".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0]").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}/bank\\.csv")))
                .andExpect(jsonPath("$.sources[1]").value(org.hamcrest.Matchers.endsWith("/card.csv")))
                .andReturn();
        String source = objectMapper.readTree(result.getResponse().getContentAsString()).get("sources").get(0).asString();

        assertThat(Files.size(WORK.resolve("uploads").resolve(source))).isEqualTo(big.length);
    }

    @Test
    void fileNamesAreSanitisedAndNeverLeaveTheUploadDirectory() throws Exception {
        MvcResult result = mvc.perform(multipart("/uploads")
                        .file(new MockMultipartFile("files", "../../evil name;rm.csv", "text/csv", "x".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0]").value(org.hamcrest.Matchers.endsWith("/evil_name_rm.csv")))
                .andReturn();
        String source = objectMapper.readTree(result.getResponse().getContentAsString()).get("sources").get(0).asString();

        assertThat(Files.exists(WORK.resolve("uploads").resolve(source))).isTrue();
        assertThat(Files.exists(WORK.resolve("evil_name_rm.csv"))).isFalse();
        assertThat(UploadController.sanitise("..\\..\\x.csv")).isEqualTo("x.csv");
        assertThat(UploadController.sanitise("...")).isEqualTo(UploadController.DEFAULT_NAME);
        assertThat(UploadController.sanitise(null)).isEqualTo(UploadController.DEFAULT_NAME);
    }

    @Test
    void reportFilesAreServedWithTheirContentTypesOrNotAtAll() throws Exception {
        Path run = WORK.resolve("reports").resolve("run-1");
        Files.createDirectories(run);
        Files.writeString(run.resolve("summary.csv"), "category,total\nHOUSING,1.00\n");
        Files.writeString(run.resolve("coach.md"), "# Coaching note\n");
        Files.writeString(run.resolve("secret.txt"), "no");

        mvc.perform(get("/reports/run-1/summary.csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string("category,total\nHOUSING,1.00\n"));
        mvc.perform(get("/reports/run-1/coach.md"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown"));
        mvc.perform(get("/reports/run-1/secret.txt")).andExpect(status().isNotFound());
        mvc.perform(get("/reports/run-1/transactions.csv")).andExpect(status().isNotFound());
        mvc.perform(get("/reports/run_1/summary.csv")).andExpect(status().isNotFound());
        mvc.perform(get("/reports/nope/summary.csv")).andExpect(status().isNotFound());
    }

    private static Path createWorkDir() {
        try {
            return Files.createTempDirectory("uploads");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
