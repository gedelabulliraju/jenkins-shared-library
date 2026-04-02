*999955l(Map configMap) {
    pipeline {
        agent {
            label 'AGENT-1'
        }
        options {
            timeout(time: 10, unit: 'MINUTES') // Timeout after 10 minutes
            disableConcurrentBuilds() // Prevent concurrent builds
        }
        parameters {
            booleanParam(name: 'deploy', defaultValue: false, description: 'Select to deploy the application after build')
        }
        environment { 
            appVersion = '' // Can be set dynamically during the pipeline
            account_id = ''
            region = "us-east-1"
            project = configMap.get("project")
            environment = ''
            component = configMap.get("component")
        }
    
        stages {
            stage('Read the app version') {
                steps {
                    script {
                        def packageJson = readJSON file: 'package.json'
                        appVersion = packageJson.version
                        echo "App Version: ${appVersion}"
                    }
                }
            }
            stage('Install Dependencies') {
                steps {
                    sh 'npm install'
                }
            }
            stage('SonarQube analysis') {
                steps {
                    script {
                        def scannerHome = tool 'sonar-8.0'
                        withSonarQubeEnv('sonar-8.0') {
                            sh "${scannerHome}/bin/sonar-scanner"
                        }
                    }
                }
            }
            stage('SQuality Gate') {
                steps {
                    timeout(time: 5, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            } 
            // stage('Create Docker Image') {
            //     steps {
            //         sh """
            //             docker build -t srlaf/backend:${appVersion} .
            //             docker images
            //         """
            //     }
            // }
            // stage('SonarQube analysis') {
            //     environment {
            //         SCANNER_HOME = tool 'sonar-8.0' //scanner config
            //     }
            //     steps {
            //         // sonar server injection
            //         withSonarQubeEnv('sonar-8.0') {
            //             sh '$SCANNER_HOME/bin/sonar-scanner'
            //             //generic scanner, it automatically understands the language and provide scan results
            //         }
            //     }
            // }
            stage('docker build & push to ecr') { 
                steps { 
                    withAWS(region: 'us-east-1', credentials: 'aws-creds') {
                        script {
                            sh "aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${account_id}.dkr.ecr.${region}.amazonaws.com"
                            sh "docker build -t ${project}/${environment}/${component}:${appVersion} ."
                            sh "docker tag ${project}/${environment}/${component}:${appVersion} ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}/${environment}/${component}:${appVersion}"
                            sh "docker push ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}/${environment}/${component}:${appVersion}"
                        }
                    }
                }
            }
            stage('Deploy') {
                 when {
                    expression { params.deploy }
                }
                steps {
                    build job: "EXPENSE/backend/${component}-cd", parameters: [
                        string(name: 'Version', value: "${appVersion}"),
                        string(name: 'environment', value: 'dev'),
                    ], wait: true
                }
            }
        }   
        // stage('Print Params'){
        //     steps{
        //         echo "Hello ${params.PERSON}"
        //         echo "Biography: ${params.BIOGRAPHY}"
        //         echo "Toggle: ${params.TOGGLE}"
        //         echo "Choice: ${params.CHOICE}"
        //         echo "Password: ${params.PASSWORD}"  
        //     }
        // }
        // stage('Approval') {
        //     input {
        //         message "Should we continue?.."
        //         ok "Yes, we should."
        //         submitter "alice,bob"
        //         parameters {
        //             string(name: 'PERSON', defaultValue: 'Mr Jenkins', description: 'Who should I say hello to?')
        //         }
        //     }   
        //     steps {
        //         echo "Hello, ${PERSON}, nice to meet you."
        //     }
        // }
    
        post {
            always {
                echo 'This will always run'
                deleteDir() // Clean up workspace
            }
            success {
                echo 'This will run only if successful'
            }
            failure {
                echo 'This will run only if failed'
            }
            unstable {
                echo 'This will run only if the run was marked as unstable'
            }
            changed {
                echo 'This will run only if the state of the pipeline has changed'
            }
        }
    }
}