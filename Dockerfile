# Builds and runs poc-04-agent-server, which hosts the agents from POC 01-03.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY poc-01-meeting-notes poc-01-meeting-notes
COPY poc-02-dependency-advisor poc-02-dependency-advisor
COPY poc-03-pr-review poc-03-pr-review
COPY poc-04-agent-server poc-04-agent-server
RUN mvn -B -q -pl poc-04-agent-server -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system agent
USER agent
COPY --from=build /src/poc-04-agent-server/target/poc-04-agent-server-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
