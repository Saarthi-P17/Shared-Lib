def call(Map config = [:]) {

    node {

        def ZAP_SERVER   = config.ZAP_SERVER
        def TARGET_URL   = config.TARGET_URL
        def BASE_URL     = config.BASE_URL
        def API_KEY      = config.API_KEY
        def SLACK_CHANNEL = config.SLACK_CHANNEL

        try {

            stage('Clean Workspace') {
                cleanWs()
            }

            stage('Check ZAP API') {
                sh "echo 'Checking ZAP API...' && curl '${ZAP_SERVER}/JSON/core/view/version/?apikey=${API_KEY}'"
            }

            stage('Check Target Endpoint') {
                sh "echo 'Checking Target Endpoint...' && curl -I ${TARGET_URL} || exit 1"
            }

            stage('Spider Scan') {
                sh "echo 'Starting Spider Scan...' && curl '${ZAP_SERVER}/JSON/spider/action/scan/?url=${BASE_URL}&apikey=${API_KEY}'"
            }

            stage('Wait for Spider Completion') {
                def status = "0"
                while (status != "100") {
                    status = sh(
                        script: "curl -s '${ZAP_SERVER}/JSON/spider/view/status/?scanId=0&apikey=${API_KEY}' | jq -r '.status'",
                        returnStdout: true
                    ).trim()
                    echo "Spider Progress: ${status}%"
                    sleep 5
                }
            }

            stage('Active Scan') {
                sh "echo 'Starting Active Scan...' && curl '${ZAP_SERVER}/JSON/ascan/action/scan/?url=${TARGET_URL}&apikey=${API_KEY}'"
            }

            stage('Wait for Active Scan Completion') {
                def status = "0"
                while (status != "100") {
                    status = sh(
                        script: "curl -s '${ZAP_SERVER}/JSON/ascan/view/status/?scanId=0&apikey=${API_KEY}' | jq -r '.status'",
                        returnStdout: true
                    ).trim()
                    echo "Active Scan Progress: ${status}%"
                    sleep 10
                }
            }

            stage('Generate Report') {
                sh "curl '${ZAP_SERVER}/OTHER/core/other/htmlreport/?apikey=${API_KEY}' -o zap_report.html"
            }

            stage('Archive Report') {
                archiveArtifacts artifacts: 'zap_report.html', fingerprint: true
            }

            currentBuild.result = 'SUCCESS'

            slackSend(
                channel: SLACK_CHANNEL,
                color: 'good',
                message: "SUCCESS: DAST Scan Passed\nJob: ${env.JOB_NAME}\nBuild: #${env.BUILD_NUMBER}\nURL: ${env.BUILD_URL}"
            )

        } catch (err) {

            currentBuild.result = 'FAILURE'

            slackSend(
                channel: SLACK_CHANNEL,
                color: 'danger',
                message: "FAILURE: DAST Scan Failed\nJob: ${env.JOB_NAME}\nBuild: #${env.BUILD_NUMBER}\nURL: ${env.BUILD_URL}"
            )

            throw err

        } finally {
            cleanWs()
            echo "Workspace cleaned"
        }
    }
}
