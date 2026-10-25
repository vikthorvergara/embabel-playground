package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.core.AgentScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SpendingCoachStructureTest {

    @Test
    void thirteenActionsAndOneExportedGoal(@TempDir Path work) {
        AgentScope scope = new AgentMetadataReader().createAgentMetadata(Fixtures.agent(Fixtures.properties(work)));

        assertThat(scope.getActions()).hasSize(13);
        assertThat(scope.getGoals()).singleElement().satisfies(goal -> {
            assertThat(goal.getExport().getName()).isEqualTo("analyse_spending");
            assertThat(goal.getExport().getRemote()).isTrue();
            assertThat(goal.getExport().getStartingInputTypes()).containsExactly(StatementUpload.class);
        });
    }

    @Test
    void everyConditionIsListedInSomeActionsPostArray() {
        Method[] methods = SpendingCoachAgent.class.getDeclaredMethods();
        Set<String> conditions = Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(Condition.class))
                .map(method -> method.getAnnotation(Condition.class).name())
                .collect(Collectors.toSet());
        Set<String> settled = Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(Action.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(Action.class).post()))
                .collect(Collectors.toSet());

        assertThat(conditions).hasSize(6);
        assertThat(settled).containsAll(conditions);
    }

    @Test
    void everyRoleHasAModelEntryInApplicationYml() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        for (String role : List.of(SpendingProperties.LAYOUT_ROLE, SpendingProperties.CATEGORISER_ROLE, SpendingProperties.COACH_ROLE)) {
            assertThat(properties.getProperty("embabel.models.llms." + role)).as(role).isNotBlank();
        }
        assertThat(properties.getProperty("embabel.agent.platform.llm-operations.data-binding.max-attempts")).isEqualTo("3");
    }
}
