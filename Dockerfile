# Build stage
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and pom.xml first (better layer caching)
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw -B -ntp dependency:go-offline

# Copy source code
COPY src ./src

# Build the application (the tests run in CI before the image is built; they need a database)
RUN ./mvnw -B -ntp clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Copy the built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring && \
    chown -R spring:spring /app

USER spring:spring

EXPOSE 8080

# Settings of the deployed demo live in application-production.properties; Cloud Run injects the values it needs.
ENV SPRING_PROFILES_ACTIVE=production

# JVM flags tuned for Cloud Run: a small, single-vCPU container that has to start quickly.
#  - SerialGC and C1-only JIT (TieredStopAtLevel=1) shorten start-up and save memory on one vCPU
#  - MaxRAMPercentage leaves headroom for metaspace, thread stacks and the Cloud SQL connector
# Override without rebuilding by setting the JAVA_OPTS environment variable on the service.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true -Dfile.encoding=UTF-8"

# dumb-init forwards signals; "exec" makes the JVM receive SIGTERM directly so shutdown stays graceful
ENTRYPOINT ["dumb-init", "--", "sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
