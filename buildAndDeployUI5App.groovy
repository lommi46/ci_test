// vars/buildAndDeployUI5App.groovy

import groovy.json.JsonSlurperClassic

def call(Map config) {

    // check parameters and set defaults
    if (!config.containsKey("isDeployOnCloud")) {
		config.put("deployCloud", false)
    } else {
      config.put("deployCloud", config.get("isDeployOnCloud"))
    }
      
    if (!config.containsKey("isDeployOnPremise")) {
        config.put("deployOnPremise", true)
    } else {
      config.put("deployOnPremise", config.get("isDeployOnPremise"))
    }
  
  // echo 'Config: ' + config
  echo 'Deploy on Cloud: ' + config.get("deployCloud")
  echo 'Deploy on Premise/Frontend-Server: ' + config.get("deployOnPremise")

    // global definition of systems etc ...
    def globals = libraryResource 'com/airplus/sap/ui5/globals.json'
    globals = new JsonSlurperClassic().parseText(globals)

    // determine the branch
    def branch = (env.GIT_BRANCH.indexOf("/") == -1) ? env.GIT_BRANCH : env.GIT_BRANCH.split("/")[1]

    // print out all environment variables
    // echo sh(returnStdout: true, script: 'env')

    // Compute the version of the applicaton
    def mtaInfo = readYaml file: 'mta.yaml'
    
    // update ID to ensure that there are no blanks
    mtaInfo.ID = mtaInfo.ID.trim()

    // Calculate the verision. 3rd level will be replaced by build numer of jenkins
    def version = mtaInfo.version
    version = version.tokenize('.')
    version = version[0] + "." + version[1] + "." + env.BUILD_ID

    // set the version in mtaInfo
    mtaInfo.version = version
    mtaInfo.modules.each {
        // only for html5 applications
        if (it.type == "html5") {
            it.parameters.version = version
        }
    }
    // now write the updated yaml file, first rm the existing one
    sh 'rm mta.yaml'
    writeYaml file: 'mta.yaml', data: mtaInfo

    println("Building application ${mtaInfo.ID} in version ${version}.")

    // build MTA application. Not only necessary for MTAR itself, also for on-premise deployment
    stage('MTA build') {
      nodejs('NodeJS10.8.0') {
        sh 'npm config set strict-ssl=false'
        sh 'java -jar /opt/mta/mtabuilder.jar --build-target=NEO --mtar=' + mtaInfo.ID + '.mtar build'
      }
    }
  
  echo 'Deployment #1'
  echo 'branch is ' + branch

    // deployment to cloud and on premise only if branch is development
    if (branch.equalsIgnoreCase("develop") /* just for testing jenkins */ || branch.equalsIgnoreCase("master")) {
      
      echo 'Starting development deployment'

        // cloud deployment if requested
        if (config.get("deployCloud")) {
            stage('cloud deployment') {        
                // Deployment on clouod via NEO SDK deployment tools -> commandline deployment
                withCredentials([
                    usernamePassword(credentialsId:'scp-client-logon', passwordVariable:'SCP_LOGON_PSW', usernameVariable:'SCP_LOGON_USR')
                ]) {
                    sh '/opt/neo-java-web-sdk/tools/neo.sh deploy-mta -h ' + globals.scp.home + ' --synchronous -a ' + \
                        globals.scp.subaccount + ' -u $SCP_LOGON_USR -p $SCP_LOGON_PSW -s ./'  + mtaInfo.ID + '.mtar --output json'
                }
            }
        }

        // on premise deployment if requested
        if (config.get("deployOnPremise")) {
            withCredentials([
                usernamePassword(credentialsId:'abap-client-logon', passwordVariable:'ABAP_LOGON_PSW', usernameVariable:'ABAP_LOGON_USR'),
                usernamePassword(credentialsId:'jenkins-nexus', passwordVariable:'NEXUS_PSW', usernameVariable:'NEXUS_USR')
            ]) {
                mtaInfo.modules.each {
                    dir(it.path) {
                        stage('on premise deployment for ' + it.name) {
                            // read information about the frontend deployment
                            def deployDesc = readJSON file: 'frontend-deployment.json'

                            // name of the archive with UI5 application
                            def archiveName = it.name + "-" + version + ".zip"
                            // check if the 'abap_deploy.zip' already exists. In case yes, remove it
                            if (fileExists(archiveName)) {
                                sh 'rm ' + archiveName
                            }
                            // Create a zip-file with the deployable content for the frontend server
                            zip zipFile: archiveName, dir: 'dist'

                            // Upload the zip file to the nexus archive
                            def archiveURL = globals.nexus.url + mtaInfo.ID + '/' + it.name + '/' + archiveName
                            sh 'curl -v -u $NEXUS_USR:$NEXUS_PSW --upload-file ' + archiveName + ' ' + archiveURL

                            // determine the request-nummer. We expect it to be the first line of the commit comment to contain (the only content)
                            // the request number
                            def lastCommit = sh (
                                script: 'git log -1',
                                returnStdout: true
                            ).trim()

                            def lines = lastCommit.split('\n');
                            def isInHeader = true
                            def transportNr = null

                            for (line in lines) {
                                if (!isInHeader) {
                                    transportNr = line.trim()
                                    break
                                }
                                if (line.trim().length() == 0) {
                                    isInHeader = false
                                }
                            }

                            println("Transport-nummer is: ${transportNr}")        

                            // call function module on frontend server to "upload" the application.
                            sh 'java -cp "/opt/jco3/sapjco3.jar:/opt/ctsconnect/ctsconnect-1.0.0-jar-with-dependencies.jar"' \
                                + ' com.airplus.sap.erp.ctsconnect.Main upload_ui5_app --host=' + globals.abapGateway.host \
                                + ' --sysnr=' + globals.abapGateway.sysnr + ' --client=' + globals.abapGateway.client \
                                + ' --user=$ABAP_LOGON_USR --pwd=$ABAP_LOGON_PSW --value=\'{\"URL\":\"' + archiveURL \
                                + '\",\"APPLICATION_NAME\":\"' + deployDesc.application + '\",\"APPLICATION_DESC\":\"' \
                                + deployDesc.description + '\",\"PACKAGE\":\"' + deployDesc.package + '\",\"REQUEST\":\"' + transportNr + '\"}\''

                            println("On-Premise deployment done.")
                        }
                    }
                }
            }
        }
    }
  
  echo 'Deployment #2'

    // if commit is on master branch then we add the created mtar archive to a transport request
    if (branch.equalsIgnoreCase("master")) {
        // only necessary for cloud applications
        if (config.get("deployCloud")) {
            // TODO Add code to attach to a transport request ... See https://github.com/SAP/devops-cm-client
            withCredentials([
                usernamePassword(credentialsId:'tansport-client-logon', passwordVariable:'TRANSPORT_LOGON_PSW', usernameVariable:'TRANSPORT_LOGON_USR')
            ]) {
                // sh "/opt/cmclient/bin/cmclient --backend-type CTS --endpoint ${globals.transport.endpoint} --user $TRANSPORT_LOGON_USR" \
                // + " -password $TRANSPORT_LOGON_PSW upload-file-to-transport ??"

                // /opt/cmclient/bin/cmclient -tCTS -ehttp://localhost/ -uDEVELOPER -pt0pspeed upload-file-to-transport ./sap-erp-jenkins.mtar -tID NPL9900            
            }
        }
    }
}
