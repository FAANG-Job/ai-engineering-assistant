pipeline {
    agent any

    stages {
        stage('Build JAR and Deploy') {
            steps {
                bat '''
@echo off
setlocal

set "JAVA_HOME=C:\\work\\FAANG-JOBS\\jdk-21.0.12.1"
set "MAVEN_HOME=C:\\work\\automation\\apache-maven-4.0.0-rc-3"
set "PODMAN_HOME=C:\\Program Files\\RedHat\\Podman"
set "PATH=%JAVA_HOME%\\bin;%MAVEN_HOME%\\bin;%PODMAN_HOME%;%PATH%"

REM Verify Podman connectivity
podman info
if errorlevel 1 exit /b %errorlevel%

REM Build JAR with tests skipped
call mvn -B clean package -DskipTests
if errorlevel 1 exit /b %errorlevel%

REM Build the updated image
podman build -t interview-service:java21 -f Dockerfile.java21 .
if errorlevel 1 exit /b %errorlevel%

REM Remove the existing container if present
podman container exists interview-service

if errorlevel 2 exit /b %errorlevel%

if not errorlevel 1 (
    podman rm -f interview-service
    if errorlevel 1 exit /b 1
)

REM Start the updated container
podman run -d --rm --name interview-service --network=pasta -p 8080:8080 interview-service:java21
exit /b %errorlevel%
'''
            }
        }
    }
}