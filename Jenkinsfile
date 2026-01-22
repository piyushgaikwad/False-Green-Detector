pipeline {
  agent any

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Run') {
      steps {
        sh '''
          echo "Testing"
          echo "Workspace:"
          pwd
          echo "Files:"
          ls -la
        '''
      }
    }
  }
}
