package io.github.vikthorvergara.playground.depadvisor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DependencyAdvisorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DependencyAdvisorApplication.class, args);
    }
}
