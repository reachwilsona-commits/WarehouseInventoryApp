# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -e -q dependency:go-offline
COPY src ./src
RUN mvn -B -e -q -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=builder /workspace/target/warehouse-inventory.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
