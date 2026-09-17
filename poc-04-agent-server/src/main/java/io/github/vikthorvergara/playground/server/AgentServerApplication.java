package io.github.vikthorvergara.playground.server;

import io.github.vikthorvergara.playground.depadvisor.DependencyAdvisorAgent;
import io.github.vikthorvergara.playground.depadvisor.MavenCentralClient;
import io.github.vikthorvergara.playground.depadvisor.MavenCentralProperties;
import io.github.vikthorvergara.playground.meetingnotes.MeetingNotesAgent;
import io.github.vikthorvergara.playground.prreview.PrReviewAgent;
import io.github.vikthorvergara.playground.prreview.ReviewProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Hosts the agents from POC 01-03 without changing their code. They are imported explicitly
 * rather than component-scanned, so their own @SpringBootApplication classes stay out of the context.
 */
@SpringBootApplication
@Import({MeetingNotesAgent.class, DependencyAdvisorAgent.class, MavenCentralClient.class, PrReviewAgent.class})
@EnableConfigurationProperties({MavenCentralProperties.class, ReviewProperties.class, ServerProperties.class})
public class AgentServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentServerApplication.class, args);
    }
}
