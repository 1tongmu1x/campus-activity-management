FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build
COPY backend/pom.xml .
COPY backend/src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:8-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
