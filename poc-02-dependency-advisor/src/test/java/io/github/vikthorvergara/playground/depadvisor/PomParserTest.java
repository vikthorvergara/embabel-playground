package io.github.vikthorvergara.playground.depadvisor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PomParserTest {

    static final String POM = """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <artifactId>demo-service</artifactId>
              <version>1.0.0</version>
              <properties>
                <guava.version>32.1.0-jre</guava.version>
              </properties>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>org.testcontainers</groupId>
                    <artifactId>testcontainers-bom</artifactId>
                    <version>1.19.0</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
              <dependencies>
                <dependency>
                  <groupId>com.google.guava</groupId>
                  <artifactId>guava</artifactId>
                  <version>${guava.version}</version>
                </dependency>
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-web</artifactId>
                </dependency>
                <dependency>
                  <groupId>com.example</groupId>
                  <artifactId>internal-lib</artifactId>
                  <version>${internal.version}</version>
                </dependency>
              </dependencies>
              <build>
                <plugins>
                  <plugin>
                    <artifactId>some-plugin</artifactId>
                    <dependencies>
                      <dependency>
                        <groupId>org.ow2.asm</groupId>
                        <artifactId>asm</artifactId>
                        <version>9.0</version>
                      </dependency>
                    </dependencies>
                  </plugin>
                </plugins>
              </build>
            </project>
            """;

    @Test
    void readsDirectAndManagedDependenciesAndResolvesProperties() {
        DependencyList list = PomParser.parse(new PomFile("pom.xml", POM));

        assertThat(list.projectName()).isEqualTo("demo-service");
        assertThat(list.dependencies()).containsExactly(
                new Dependency("com.google.guava", "guava", "32.1.0-jre"),
                new Dependency("org.testcontainers", "testcontainers-bom", "1.19.0"));
        assertThat(list.skipped()).containsExactly(
                "org.springframework.boot:spring-boot-starter-web",
                "com.example:internal-lib");
    }

    @Test
    void rejectsDoctypeDeclarations() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE p [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><project>&x;</project>";

        assertThatThrownBy(() -> PomParser.parse(new PomFile("evil.xml", xxe)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
