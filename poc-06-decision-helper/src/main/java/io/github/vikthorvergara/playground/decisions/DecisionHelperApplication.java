package io.github.vikthorvergara.playground.decisions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DecisionHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionHelperApplication.class, args);
    }
}
