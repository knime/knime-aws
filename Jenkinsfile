#!groovy
def BN = (BRANCH_NAME == 'master' || BRANCH_NAME.startsWith('releases/')) ? BRANCH_NAME : 'releases/2022-09'

library "knime-pipeline@$BN"

properties([
    pipelineTriggers([
        upstream("knime-json/${env.BRANCH_NAME.replaceAll('/', '%2F')}")
    ]),
    parameters(workflowTests.getConfigurationsAsParameters()),
    buildDiscarder(logRotator(numToKeepStr: '5')),
    disableConcurrentBuilds()
])

try {
    // provide the name of the update site project
    knimetools.defaultTychoBuild('org.knime.update.aws')

    workflowTests.runTests(
        dependencies: [
                // yes, we really need all this stuff. knime-cloud pulls in most of it...
		    repositories:  [
                'knime-aws',
                'knime-cloud',
                'knime-database',
                'knime-datageneration',
                'knime-expressions',
                'knime-filehandling',
                'knime-jep',
                'knime-js-base',
                'knime-json',
                'knime-textprocessing',
            ],
        ]
    )

    stage('Sonarqube analysis') {
        env.lastStage = env.STAGE_NAME
        workflowTests.runSonar()
    }
} catch (ex) {
    currentBuild.result = 'FAILURE'
    throw ex
} finally {
    notifications.notifyBuild(currentBuild.result);
}

/* vim: set shiftwidth=4 expandtab smarttab: */
