FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY flight-service/pom.xml flight-service/pom.xml
COPY event-processing-service/pom.xml event-processing-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
RUN mvn -q -pl event-processing-service -am dependency:go-offline
COPY event-processing-service/src event-processing-service/src
RUN mvn -q -pl event-processing-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/event-processing-service/target/event-processing-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
