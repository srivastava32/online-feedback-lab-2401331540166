pipeline {
    agent any
    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timeout(time: 10, unit: 'MINUTES')
    }
    stages {
        stage('Checkout') {
            steps {
                script {
                    if (env.LAB_SOURCE_DIR) {
                        sh 'cp -R "$LAB_SOURCE_DIR/src" "$LAB_SOURCE_DIR/pom.xml" "$LAB_SOURCE_DIR/checkstyle.xml" .'
                    } else {
                        checkout scm
                    }
                }
            }
        }
        stage('Maven build and test') {
            steps {
                sh 'mvn --batch-mode --no-transfer-progress clean package'
            }
        }
    }
    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: false
        }
        success {
            archiveArtifacts artifacts: 'target/online-feedback.jar', fingerprint: true
        }
    }
}
