# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy root POM and all source modules for Maven reactor validation
COPY pom.xml .
COPY common ./common
COPY api-gateway ./api-gateway
COPY auth-service ./auth-service
COPY project-manager ./project-manager
COPY ingestion-service ./ingestion-service

# Build api-gateway
RUN mvn clean package -pl common,api-gateway -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/api-gateway/target/*.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
