pipeline {
  agent any

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('GitHub API (simple)') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'Checker1',
          usernameVariable: 'UserName',
          passwordVariable: 'GH_TOKEN'
        )]) {
          sh '''
            set -e
            curl -s -H "Authorization: Bearer $GH_TOKEN" \
                 -H "Accept: application/vnd.github+json" \
                 https://api.github.com/rate_limit
          '''
        }
      }
    }

    stage('Run') {
      steps {
        sh '''
          echo "Hello from Jenkins!"
          pwd
          ls -la
        '''
      }
    }
  }
}
