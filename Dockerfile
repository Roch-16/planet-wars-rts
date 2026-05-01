# ---------- Stage 1: Build with Gradle ----------
FROM gradle:8.12-jdk21 AS builder

WORKDIR /home/gradle/project

# Copy entire project into container
COPY . .

# Use the Gradle wrapper to build a fat JAR (includes dependencies)
# Ensure wrapper is executable then run shadowJar to produce client-server.jar
RUN chmod +x ./gradlew && ./gradlew clean :app:shadowJar -x test

# ---------- Stage 2: Runtime with Java only ----------
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy fat JAR from builder stage
COPY --from=builder /home/gradle/project/app/build/libs/client-server.jar app.jar

# Expose WebSocket port
EXPOSE 8080

# Run the agent
CMD ["java", "-jar", "app.jar"]