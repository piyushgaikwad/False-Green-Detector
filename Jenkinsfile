pipeline {
  agent any

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('GitHub rate limit') {
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

    stage('GitHub githubApp') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'githubapp-jenkins',
          usernameVariable: 'U',
          passwordVariable: 'GH_TOKEN'
        )]) {
          sh '''
            set -euo pipefail
            umask 077
            TOKEN_FILE=$(mktemp)
        
            # write token to file (no printing)
            printf "%s" "$GH_TOKEN" > "$TOKEN_FILE"
        
            echo "Token saved to: $TOKEN_FILE"
            echo "Now you can open it locally on the Jenkins machine if you really need."
            # cleanup
            rm -f "$TOKEN_FILE"
          '''
        }
      }
    }

    stage('GitHub githubApp1') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'githubapp-jenkins',
          usernameVariable: 'U',
          passwordVariable: 'GH_TOKEN'
        )]) {
          sh '''
            set -euo pipefail
            umask 077
            TOKEN_FILE1=$(mktemp)
        
            # write token to file (no printing)
            printf "%s" "$GH_TOKEN" > "$TOKEN_FILE1"
        
            echo "Token saved to: $TOKEN_FILE1"
            echo "Now you can open it locally on the Jenkins machine if you really need."
            # cleanup
            rm -f "$TOKEN_FILE1"
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
