pipeline {
    agent any

    stages {
        stage('Build JAR') {
            steps {
                bat '''
@echo off
setlocal
set "JAVA_HOME=C:\\work\\FAANG-JOBS\\jdk-21.0.12.1"
set "MAVEN_HOME=C:\\work\\automation\\apache-maven-4.0.0-rc-3"
set "PATH=%JAVA_HOME%\\bin;%MAVEN_HOME%\\bin;%PATH%"

#For now I am not running unit test.
call mvn -B clean package -DskipTests
# Build the updated image
podman build -t interview-service:java21 -f Dockerfile.java21 .

# Stop the old container
podman stop interview-service

# Start a new container from the updated image
podman run -d --rm --name interview-service --network=pasta -p 8080:8080 interview-service:java21

exit /b %errorlevel%
'''
            }
        }
    }
}