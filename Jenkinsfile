pipeline {
    agent any

    stages {
        stage('Build JAR') {
            steps {
                bat 'mvn -B clean package -DskipTests'
            }
        }

        stage('Archive JAR') {
            steps {
                archiveArtifacts artifacts: 'target/*.jar'
            }
        }
    }
}