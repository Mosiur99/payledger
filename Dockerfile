# syntax=docker/dockerfile:1
# One Dockerfile for both services: docker build --build-arg SERVICE=payments-service .
ARG SERVICE=payments-service

FROM eclipse-temurin:25-jdk AS build
ARG SERVICE
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY payments-service/pom.xml payments-service/
COPY notification-service/pom.xml notification-service/
COPY ${SERVICE}/src ${SERVICE}/src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl ${SERVICE} -am package -DskipTests \
 && cp ${SERVICE}/target/${SERVICE}-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
# Layers change from rarely to often, so a code change only rebuilds the last small layer.
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "application.jar"]
