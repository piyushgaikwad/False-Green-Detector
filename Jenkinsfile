pipeline {
  agent any
  stages {
    stage('Token fingerprint test') {
      steps {
        script {
          def fp1 = ''
          def fp2 = ''

          withCredentials([usernamePassword(credentialsId: 'GithubApp',
            usernameVariable: 'USER', passwordVariable: 'GH_TOKEN')]) {

            fp1 = sh(returnStdout: true, script: '''
              printf "%s" "$GH_TOKEN" | sha256sum | awk '{print $1}' | cut -c1-12
            ''').trim()
          }

          // small delay (optional)
          sleep 2

          withCredentials([usernamePassword(credentialsId: 'GithubApp',
            usernameVariable: 'USER', passwordVariable: 'GH_TOKEN')]) {

            fp2 = sh(returnStdout: true, script: '''
              printf "%s" "$GH_TOKEN" | sha256sum | awk '{print $1}' | cut -c1-12
            ''').trim()
          }

          echo "Token FP1: ${fp1}"
          echo "Token FP2: ${fp2}"
          echo "Same token? " + (fp1 == fp2 ? "YES (reused/cached)" : "NO (new token minted)")
        }
      }
    }
  }
}
