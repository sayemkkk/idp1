# Stage 1: Build the Java application
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY . .

# Compile all .java files from src into the out directory
RUN mkdir -p out && javac -cp "lib/*" -d out $(find src -name "*.java")

# Stage 2: Run the application
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy compiled classes, libraries, and public web assets from the builder stage
COPY --from=builder /app/out /app/out
COPY --from=builder /app/lib /app/lib
COPY --from=builder /app/public /app/public

# Create a directory for Render's persistent SQLite disk
RUN mkdir -p /data

# Expose port 8080 (Render maps this automatically)
EXPOSE 8080

# Run the Java application (Note: Linux classpath uses colon ':' instead of semicolon ';')
CMD ["java", "-cp", "out:lib/*", "com.studentreview.Main"]
