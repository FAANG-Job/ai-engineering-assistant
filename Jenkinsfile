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

call mvn -B clean package -DskipTests
exit /b %errorlevel%
'''
            }
        }
    }
}