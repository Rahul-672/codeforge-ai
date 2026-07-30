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

# Build ingestion-service
RUN mvn clean package -pl common,ingestion-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install git package if system commands are ever invoked
RUN apk add --no-cache git

# Copy built jar from builder stage
COPY --from=builder /app/ingestion-service/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
