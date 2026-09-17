package io.github.vikthorvergara.playground.server;

import com.embabel.agent.api.common.autonomy.AgentProcessExecution;
import com.embabel.agent.api.common.autonomy.ProcessExecutionException;
import com.embabel.agent.core.Goal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agents")
class AgentController {

    static final String PROCESS_ID_HEADER = "X-Agent-Process-Id";

    private final ExportedGoals goals;
    private final GoalRunner runner;
    private final ObjectMapper objectMapper;

    AgentController(ExportedGoals goals, GoalRunner runner, ObjectMapper objectMapper) {
        this.goals = goals;
        this.runner = runner;
        this.objectMapper = objectMapper;
    }

    record GoalInfo(String name, String description, String inputType) {
    }

    /**
     * @param markdown the same text an MCP client receives for this run
     */
    record RunResponse(String processId, String goal, String markdown, Object result) {
    }

    @GetMapping
    List<GoalInfo> list() {
        return goals.all().stream()
                .map(goal -> new GoalInfo(ExportedGoals.toolName(goal), goal.getDescription(),
                        ExportedGoals.inputType(goal).getSimpleName()))
                .toList();
    }

    @PostMapping("/{goal}")
    ResponseEntity<RunResponse> run(@PathVariable("goal") String name, @RequestBody String body) {
        Goal goal = goals.byToolName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No exported goal named " + name));
        Object input;
        try {
            input = objectMapper.readValue(body, ExportedGoals.inputType(goal));
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid input for " + name + ": " + e.getOriginalMessage());
        }
        AgentProcessExecution execution = runner.run(goal, input);
        String processId = execution.getAgentProcess().getId();
        Object output = execution.getOutput();
        return ResponseEntity.ok()
                .header(PROCESS_ID_HEADER, processId)
                .body(new RunResponse(processId, name, output.toString(), output));
    }

    @ExceptionHandler(ProcessExecutionException.class)
    ResponseEntity<Map<String, String>> failed(ProcessExecutionException e) {
        String processId = e.getAgentProcess().getId();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .header(PROCESS_ID_HEADER, processId)
                .body(Map.of("processId", processId, "error", String.valueOf(e.getMessage())));
    }
}
