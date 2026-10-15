package io.github.vikthorvergara.playground.spending;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpendingCoachApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpendingCoachApplication.class, args);
    }
}
