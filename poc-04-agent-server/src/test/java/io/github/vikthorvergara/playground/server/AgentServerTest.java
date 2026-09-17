package io.github.vikthorvergara.playground.server;

import com.embabel.agent.mcpserver.McpExportToolCallbackPublisher;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import io.github.vikthorvergara.playground.meetingnotes.ActionItem;
import io.github.vikthorvergara.playground.meetingnotes.ActionItemDraft;
import io.github.vikthorvergara.playground.meetingnotes.Priority;
import io.github.vikthorvergara.playground.prreview.Critique;
import io.github.vikthorvergara.playground.prreview.ReviewComment;
import io.github.vikthorvergara.playground.prreview.ReviewDraft;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole server with all three agents. Only the LLM is mocked.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ANTHROPIC_API_KEY=test-key",
        "agent-server.api-key=" + AgentServerTest.API_KEY,
        "review.max-iterations=2"
})
class AgentServerTest extends EmbabelMockitoIntegrationTest {

    static final String API_KEY = "test-api-key";

    private static final String NOTES = "# Sprint 42\\nAttendees: Ana, Bruno\\nBruno: I'll fix the checkout bug.";
    private static final String DIFF = "diff --git a/App.java b/App.java\\n+++ b/App.java\\n+String s = null; s.length();";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private McpExportToolCallbackPublisher mcpTools;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void requestsWithoutTheApiKeyAreRejected() throws Exception {
        mvc.perform(get("/agents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/agents").header(ApiKeyFilter.HEADER, "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void listsTheThreeExportedGoals() throws Exception {
        mvc.perform(get("/agents").header(ApiKeyFilter.HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains(
                        "advise_dependency_upgrades", "extract_action_items", "review_diff")))
                .andExpect(jsonPath("$[1].inputType").value("MeetingTranscript"));
    }

    @Test
    void mcpPublishesTheSameGoalsAsTools() {
        assertThat(mcpTools.getToolCallbacks()).extracting(tool -> tool.getToolDefinition().name())
                .contains("advise_dependency_upgrades", "extract_action_items", "review_diff");
    }

    @Test
    void extractActionItemsOverRestAndMcpGiveTheSameAnswer() throws Exception {
        whenCreateObject(prompt -> prompt.contains("Extract every action item"), ActionItemDraft.class)
                .thenReturn(new ActionItemDraft(List.of(
                        new ActionItem("Fix the checkout bug", "Bruno", LocalDate.of(2026, 9, 18), Priority.HIGH))));
        String input = "{\"text\": \"" + NOTES + "\"}";

        MvcResult result = mvc.perform(post("/agents/extract_action_items")
                        .header(ApiKeyFilter.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(input))
                .andExpect(status().isOk())
                .andExpect(header().exists(AgentController.PROCESS_ID_HEADER))
                .andExpect(jsonPath("$.result.items[0].owner").value("Bruno"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.get("processId").asString())
                .isEqualTo(result.getResponse().getHeader(AgentController.PROCESS_ID_HEADER));
        assertThat(body.get("markdown").asString())
                .isEqualTo("## Sprint 42\n\n- [HIGH] Fix the checkout bug (owner: Bruno, due 2026-09-18)");
        assertThat(mcpTool("extract_action_items").call(input)).contains(body.get("markdown").asString());
        assertThat(meterRegistry.counter("agent.runs", "agent", "MeetingNotesAgent.prioritise", "outcome", "completed").count())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void reviewDiffIsAutoApproved() throws Exception {
        whenCreateObject(prompt -> prompt.contains("You are reviewing a pull request"), ReviewDraft.class)
                .thenReturn(new ReviewDraft("NPE.", List.of(
                        new ReviewComment("App.java", 1, ReviewComment.Severity.BLOCKER, "s is always null."))));
        whenCreateObject(prompt -> prompt.contains("checking another reviewer's work"), Critique.class)
                .thenReturn(new Critique(0.9, List.of(new Critique.Verdict(0, true, "Right.")), "Good."));

        mvc.perform(post("/agents/review_diff")
                        .header(ApiKeyFilter.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diff\": \"" + DIFF + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.autoApproved").value(true))
                .andExpect(jsonPath("$.result.rounds").value(1))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("s is always null.")));
    }

    @Test
    void dependencyAdvisorRunsWithoutLlmOrNetworkWhenNothingCanBeChecked() throws Exception {
        String pom = "<project><artifactId>demo</artifactId><dependencies><dependency><groupId>a</groupId><artifactId>b</artifactId></dependency></dependencies></project>";

        mvc.perform(post("/agents/advise_dependency_upgrades")
                        .header(ApiKeyFilter.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pom", pom))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.skipped[0]").value("a:b"))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("Everything we could check is up to date.")));
    }

    @Test
    void unknownGoalAndBadInputAreClientErrors() throws Exception {
        mvc.perform(post("/agents/nope").header(ApiKeyFilter.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/agents/review_diff").header(ApiKeyFilter.HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }

    private ToolCallback mcpTool(String name) {
        return mcpTools.getToolCallbacks().stream()
                .filter(tool -> tool.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
