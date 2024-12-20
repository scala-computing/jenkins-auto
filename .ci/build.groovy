#!groovy

//Combine serial, openmp and mpi testcases in one report and run last_only_once.csh
def appendOutput(stageName) {
    return {
        stage ("${stageName}") {
            def dirpath = """$WORKSPACE/$BUILD_NUMBER/terraform"""
            dir(dirpath) {
                sh """
                echo $dirpath

                if [ -d "/tmp/raw_output_$BUILD_NUMBER/" ] 
                then
                    echo "Directory exists."
                    sudo -S rm -rfv /tmp/raw_output_$BUILD_NUMBER/ 
                else
                    echo "/tmp/raw_output_$BUILD_NUMBER/ not found moving on"
                fi

                if [ -d "/tmp/raw_output_$BUILD_NUMBER/final_output" ] 
                then
                    echo "Directory exists."
                    sudo -S rm -rfv /tmp/raw_output_$BUILD_NUMBER/final_output
                else
                    echo "/tmp/raw_output_$BUILD_NUMBER/final_output not found moving on"
                fi

                if [ -d "/tmp/coop-repo_$BUILD_NUMBER" ] 
                then
                    echo "Directory exists."
                    sudo -S rm -rfv /tmp/coop-repo_$BUILD_NUMBER
                else
                    echo "/tmp/coop-repo_$BUILD_NUMBER not found moving on"
                fi

                if [ -d "/tmp/Success_files_$BUILD_NUMBER" ] 
                then
                    echo "Directory exists."
                    sudo -S rm -rfv /tmp/Success_files_$BUILD_NUMBER
                else
                    echo "/tmp/Success_files_$BUILD_NUMBER not found moving on"
                fi

                mkdir -pv /tmp/raw_output_$BUILD_NUMBER/
                mkdir -pv /tmp/raw_output_$BUILD_NUMBER/final_output
                mkdir -pv /tmp/coop-repo_$BUILD_NUMBER
                mkdir -pv /tmp/Success_files_$BUILD_NUMBER

                aws s3 cp s3://wrf-testcase/raw_output/$BUILD_NUMBER/ /tmp/raw_output_$BUILD_NUMBER/ --region us-east-1 --recursive
                git clone --branch regression+feature https://github.com/wrf-model/wrf-coop.git /tmp/coop-repo_$BUILD_NUMBER/wrf-coop
                csh /tmp/coop-repo_$BUILD_NUMBER/wrf-coop/build.csh /tmp/coop-repo_$BUILD_NUMBER/wrf-coop /tmp/coop-repo_$BUILD_NUMBER/wrf-coop
                sh $WORKSPACE/$BUILD_NUMBER/terraform/part.sh
                """
                //OK=$(diff -q $file1 $file2) && echo "$fileone vs $file2 status = $OK"
                
                // sh"""
                //  aws s3 cp /tmp/raw_output_$BUILD_NUMBER/final_output/ s3://wrf-testcase/cost_optimized_output/$BUILD_NUMBER/ --region us-east-1 --recursive
                // """
            }
        }
    }
}                
                //  sudo -S rm -rfv /tmp/raw_output_$BUILD_NUMBER
                //  aws s3 cp s3://wrf-testcase/output/$BUILD_NUMBER/ output_testcase/ --region us-east-1 --recursive

//Download output of test cases
def downloadOutput(stageName) {
    return{
        stage ("${stageName}") {
            def dirpath = """$WORKSPACE/$BUILD_NUMBER/terraform"""
            dir(dirpath) {
                sh """
                echo $dirpath
                """
                sh """
                mkdir output_testcase
                cp /tmp/raw_output_$BUILD_NUMBER/final_output/* output_testcase/
                cp /tmp/raw_output_$BUILD_NUMBER/email_01.txt output_testcase/
                cp /tmp/Success_files_$BUILD_NUMBER/* output_testcase/
                zip -r $WORKSPACE/$BUILD_NUMBER/wrf_output.zip output_testcase
                """
            }
        }
    }
}

//Check Instance Runngin Status for test cases
def checkinstancerunningStatus(stageName) {
    return {
        stage("${stageName}") {
            echo "Running stage : ${stageName}"
            script {

                while(Instanceflag()==true) {
                def flag=Instanceflag()
                    if(flag==true) {
                        print("Instances are still running")
                    } else {
                        print("Instances are stopped")
                        print(flag)
                    break
                    }
                }
            }
        }
    }
}

def terraformStage(stageName) {
    return {
        stage("${stageName}") {
            // Setting Build number for tagging with terraform
            echo "Running stage: ${stageName} and build number : ${BUILD_NUMBER}"
            echo "Appending ${BUILD_NUMBER} in vars.tf"
            echo "These are environment variables for branch and Github repo\n"
            sh """
            chmod 777 -R $WORKSPACE/$BUILD_NUMBER
            mkdir -pv $WORKSPACE/$BUILD_NUMBER/WRF
            echo "Cloning repo into:   $WORKSPACE/$BUILD_NUMBER/WRF "
            git clone --single-branch --branch master https://github.com/scala-computing/jenkins-auto.git $WORKSPACE/$BUILD_NUMBER/WRF
            sed -i 's/default = "wrf-test"/default = "build-${BUILD_NUMBER}"/' $WORKSPACE/$BUILD_NUMBER/WRF/.ci/terraform/vars.tf
            """        
            if (label=='"DO_KPP_TEST"') {        
                for (int j=0;j<=60;j++) {
                    sh"""
                    sed -i "3i export GIT_URL=$repo_url\\nexport GIT_BRANCH=$fork_branchName" $WORKSPACE/$BUILD_NUMBER/WRF/.ci/terraform/wrf_testcase_"$j".sh
                    sed -i '\$i cd /home/ubuntu/ && bash upload_script.sh output_$j $BUILD_NUMBER' $WORKSPACE/$BUILD_NUMBER/WRF/.ci/terraform/wrf_testcase_"$j".sh
                    """
                }
            } else {
                sh """
                sed -i 's/variable "instance_count" {default = 58 }/variable "instance_count" {default = 60 } /g' $WORKSPACE/$BUILD_NUMBER/WRF/.ci/terraform/vars.tf
                """
                for (int j=0;j<=58;j++) {
                    sh"""
                    sed -i "3i export GIT_URL=$repo_url\\nexport GIT_BRANCH=$fork_branchName" $WORKSPACE/$BUILD_NUMBER/WRF/.ci/terraform/wrf_testcase_"$j".sh
                    sed -i '\$i cd /home/ubuntu/ && bash upload_script.sh output_$j $BUILD_NUMBER' $WORKSPACE/$BUILD_NUMBER/WRF/.ci/terraform/wrf_testcase_"$j".sh
                    """
                }
            }
            sh """
            cd $WORKSPACE/$BUILD_NUMBER/WRF/.ci/terraform && terraform init && terraform plan && terraform apply -auto-approve
            """
        }
    }
}

/***
Func to check if instane with current tag is running or not
***/
    // aws ec2 describe-instances --query 'Reservations[*].Instances[*].[InstanceId]' --filters Name=instance-state-name,Values=running  "Name=tag:Name,Values=build-$BUILD_NUMBER" --region us-east-1
def Instanceflag() {
    instanceId="""
    aws ec2 describe-instances --query 'Reservations[*].Instances[*].[InstanceId]' --filters Name=instance-state-name,Values=running  "Name=tag:Name,Values=wrf_testcase_*_build-$BUILD_NUMBER" --region us-east-1
    """
    instance=sh(script: instanceId, returnStdout: true)
    def running
    if(instance.size()<=3) {
        running=false
    } else {
        running=true
    }
    return running
}

/***
    Kill current build for this JOB
***/
def killall_jobs() {
    def jobname = env.JOB_NAME
    def buildnum = env.BUILD_NUMBER.toInteger()
    echo "${buildnum}"
    echo "From kill all jobs"
    echo "${jobname}"
    def rmi = """
    mkdir -pv $WORKSPACE/$BUILD_NUMBER/WRF
    echo "Cloning repo into:   $WORKSPACE/$BUILD_NUMBER/WRF "
    git clone --single-branch --branch master https://github.com/scala-computing/jenkins-auto.git $WORKSPACE/$BUILD_NUMBER/WRF   
    """
    rm=sh(script: rmi,returnStdout: true)
    def job = Jenkins.instance.getItemByFullName(jobname)
    println("Kill task because commits have been found in .md and .txt files for $BUILD_NUMBER or either action is other than open/synchronise")
}

//Run any shell script with this function
def mysh(cmd) {
    return sh(script: cmd, returnStdout: true).trim()
}

// Func to return boolean true if in PR we have only .md/.txt files and False in case of anything else
def filterReadme(cmd) {
    def readmelist=[]
    readmelist.add(sh(script: cmd, returnStdout: true).trim())
    println("List of changed file are:")
    println(readmelist)
    def readme=readmelist.every {it =~ /^.*\b(README.namelist|README.physics_files|README.rasm_diag|README.tslist|README)\b.*$/}
    return readme
}

def filterFiles(cmd) {
    def list=[]
    list.add(sh(script: cmd, returnStdout: true).trim())
    println("List of changed file are:")
    println(list)
    def bool=list.every { it =~ /(?i)\.(?:md|txt)$/ }
    return bool
}

pipeline {
    agent any
    options {
        timeout(time: 1, unit: 'HOURS')   // timeout on whole pipeline job
    }

    parameters {
        string(name: 'payload', defaultValue: '', description: 'github payload')
    }

    stages {
        stage('Clean Workspace') {
            steps ("Cleaning workspace") {
                sh '''
                sudo -S rm -rfv $WORKSPACE/$BUILD_NUMBER
                sudo -S rm -rfv $WORKSPACE/wrf_output.zip
                sudo -S rm -rfv /tmp/raw*
                terraform -v 
                '''
            }
        }

        stage('Setting Variables From Webhook Payload') {
            steps ("Setting variables") {
                withCredentials([string(credentialsId: 'ncar-git-token', variable: 'gitToken')]) {
                    sh '''
                    mkdir -pv $WORKSPACE/$BUILD_NUMBER
                    chmod 777 -R $WORKSPACE/$BUILD_NUMBER
                    echo $payload > $WORKSPACE/$BUILD_NUMBER/sample.json
                    '''
                    script {
                        // Baseowner
                        def sh18= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .pull_request.base.user.login
                        """
                        env.baseowner=mysh(sh18)
                        // pull request number
                        def sh17= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .number
                        """
                        env.pullnumber=mysh(sh17)
                        // action variable
                        def sh16= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .action
                        """
                        env.action=mysh(sh16)
                        // SHA ID
                        def sh14= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .pull_request.head.sha
                        """
                        env.sha=mysh(sh14)
                        
                        def sh1= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .pull_request.id
                        """
                        pr_id=mysh(sh1)
                        println(pr_id)

                        def sh2= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .pull_request.head.repo.name
                        """
                        repo_name=mysh(sh2)
                        println(repo_name)

                        def sh3= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .pull_request.head.ref
                        """
                        fork_branchName=mysh(sh3)
                        println(fork_branchName)

                        def sh4= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .pull_request.head.user.html_url
                        """
                        fork_url=mysh(sh4)
                        println(fork_url)

                        def sh5= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .pull_request.base.ref
                        """
                        base_branchName=mysh(sh5)
                        println(base_branchName)

                        def sh6= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .pull_request.base.user.html_url
                        """
                        base_url=mysh(sh6)
                        println(base_url)

                        def sh7= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq .pull_request.head.repo.clone_url
                        """
                        env.repo_url=mysh(sh7)
                        println(repo_url) // Github url

                        def sh11= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq '.pull_request.user.login'
                        """
                        env.githubuserName=mysh(sh11)  // Github UserName
                        // Cloning the forked repository

                        sh """
                        mkdir -pv $WORKSPACE/$BUILD_NUMBER/forked_repo
                        git clone -b $fork_branchName --single-branch $repo_url $WORKSPACE/$BUILD_NUMBER/forked_repo
                        git config --global --add safe.directory $WORKSPACE/$BUILD_NUMBER/forked_repo
                        """
                        def sh8= """
                        cd $WORKSPACE/$BUILD_NUMBER/forked_repo && git rev-parse HEAD
                        """
                        env.commitID=mysh(sh8)
                        // Email ID of user submitting the pull request
                        def sh12= """
                        cd $WORKSPACE/$BUILD_NUMBER/forked_repo && git --no-pager show -s --format='%ae' $commitID
                        """
                        // def labels= """
                        // cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq '.pull_request.labels[].name'|head -1 || true
                        def labels= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq '.pull_request.labels[].name'
                        """
                        def retest= """
                        cd $WORKSPACE/$BUILD_NUMBER && cat sample.json | jq '[.pull_request.labels[].name] | any(. == "Retest")'
                        """
                        env.retest=mysh(retest)
                        env.label=mysh(labels)
                        env.eMailID=mysh(sh12)
                        println("Commit ID is")
                        println(commitID)
                        println("Github User Name")
                        println(githubuserName)
                        println("Email id of the user is")
                        println(eMailID.toString())
                        println("Label is")
                        println(label)

                        if (
                        action == '"opened"' || 
                        action == '"synchronize"' || 
                        action == '"reopened"' || 
                        // (action == '"labeled"' && label == '"Retest"')
                        (action == '"labeled"' && retest == 'true')
                        ) {
                        // Github status for current build
                        sh """
                        curl -s "https://api.GitHub.com/repos/wrf-model/WRF/statuses/$sha" \
                        -H "Content-Type: application/json" \
                        -H "Authorization: token $gitToken" \
                        -X POST \
                        -d '{"state": "pending","context": "WRF-BUILD-$BUILD_NUMBER", "description": "WRF regression test running", "target_url": "https://ncar_jenkins.scalacomputing.com/job/WRF-Feature-Regression-Test/$BUILD_NUMBER/console"}'
                        """
                        }
                    }
                }
            }
        }

        stage('Checking commit to see type of file that was changed') {
            steps('.md/.txt/README.namelist/README.physics_files/README.rasm_diag/README.tslist/README') {
                script {
                    echo "Checking commits to see if changes were made to .md/.txt/README.namelist/README.physics_files/README.rasm_diag/README.tslist/README Files and Running/Failing the test cases depending on action being open/synchronise/edited/review_requested/reopened"
                    echo "$BUILD_NUMBER"
                    echo "fork_repo_$BUILD_NUMBER"
                    echo "Pull number is: $pullnumber"
                    def sh9="""
                    curl -s https://patch-diff.githubusercontent.com/raw/wrf-model/WRF/pull/${pullnumber}.patch| grep -i "SUBJECT" | tail -n 1 | awk '{\$1="";\$2="";\$3=""; print \$0}'
                    """
                    env.prComment=mysh(sh9)
                    println("Checking for list of file changes in this commit")
                    def sh13="""
                    cd $WORKSPACE/$BUILD_NUMBER/forked_repo 
                    git diff-tree --no-commit-id --name-only -r $commitID
                    """
                    bool=filterFiles(sh13)
                    readme=filterReadme(sh13)
                    /*
                    Check for files with .md/.txt extension or README.namelist/README.physics_files/README.rasm_diag/README.tslist/README are in a pull request. 
                    It returns true if every file is .md/.txt or README.namelist/README.physics_files/README.rasm_diag/README.tslist/README are else it returns false.
                    */
                    println("################## Action ####################")
                    println(action)
                    println("##############################################")

                    // if(bool ==true || label=='"DO_NO_TEST"'|| label == '"Staging"'|| label != '"Feature"') { // Old if condition changed with enhancements
                    // if ( readme == true || bool == true || label =='"DO_NO_TEST"'|| label == '"Staging"'|| label =='"Previous-pipeline"' || label =='"Davegill-repo"'  ) 
                    if ( 
                        readme == true || 
                        bool == true || 
                        label.any { it.contains('"DO_NO_TEST"') } || 
                        label.any { it.contains('"Staging"') } || 
                        label.any { it.contains('"Previous-pipeline"') } || 
                        label.any { it.contains('"Davegill-repo"') }  
                        // || label !='"New-Repo"'
                        ) { 
                        println("Entering if condition")
                        killall_jobs()
                        currentBuild.result = 'ABORTED'
                    /*
                    Check for action is open/sycnhronise and continue the build job
                    */
                    /*
                    Kill the job if neither of the above conditions are true
                    */
                    // } else if ( action == '"opened"' || action == '"synchronize"' || action == '"reopened"' ) {
                    
                    } else if (
                        action == '"opened"' || 
                        action == '"synchronize"' || 
                        action == '"reopened"' || 
                        // (action == '"labeled"' && label == '"Retest"')
                        (action == '"labeled"' && retest == 'true')
                    ) {
                        println("Proceeding to another stage because commits have not been found in .md/.txt files and action is open/sycnhronize/reopened")
                        // Running terraform deployment
                        println("Deploying terraform:")
                        terraformStage("Running Terraform").call()
                        println("Terraform deployment finished. Now checking the status of test cases running/finished:")

                        // check test cases running status
                        checkinstancerunningStatus("Check Test cases running status").call()
                        println("Test Cases finished running. Now downloading the output of test cases from S3 on to Jenkins server")

                        // combines outputs and makes comparisons to evaluate pass/fail
                        appendOutput("Backup appended output files to S3").call()
                        println("The test cases have been appended and backed up to S3 output folder")

                        // Downloads output from S3 to Jenkins server
                        downloadOutput("Download output of the current Test build").call()
                        println("Test cases downloaded successfully. Now sending e-mail to the stakeholders. Now ready to send e-mail notification")
                    } else {
                        println("Entering else condition because neither commits have been found in .md/.txt/README.namelist/README.physics_files/README.rasm_diag/README.tslist/README files and action is not equal to open/synchronise/edited")
                        killall_jobs()
                        currentBuild.result = 'ABORTED'
                        error('Stopping early…')
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                withCredentials([string(credentialsId: 'ncar-git-token', variable: 'gitToken')]) {
                    /*
                    Setting some more variables for test results
                    */
                    env.E=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && ls -1 | grep output_ | wc -l""")
                    env.F=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -a " START" output_* | grep -av "CLEAN START" | grep -av "SIMULATION START" | grep -av "LOG START" | wc -l""")
                    env.G=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -a " = STATUS" output_* | wc -l""")
                    env.H=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -a "status = " output_* | wc -l""")
                    env.I=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -a " = STATUS" output_* | grep -av "0 = STATUS" | wc -l""")
                    env.J=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -a "status = " output_* | grep -av "status = 0" | wc -l """)
                    env.K="None"
                    env.L="None"
                    env.M=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -m1 "Number of Tests" email_01.txt | cut -d ':' -f2""")
                    env.N=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -m1 "Number of Builds" email_01.txt | cut -d ':' -f2""")
                    env.O=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -m1 "Number of Simulations" email_01.txt | cut -d ':' -f2""")
                    env.P=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -m1 "Number of Comparisons" email_01.txt | cut -d ':' -f2""")
                    println("Printing I and J")
                    println(env.I)
                    println(env.J)
                    if (env.I!="0") {
                        print("Entering if block for variabel K")
                        env.K=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -a " = STATUS" output_* | grep -av "0 = STATUS" || true """ )
                        // if I is not 0, then include this text
                    }
                    if(env.J!="0") {
                        print("Entering else if for variable L")
                        env.L=mysh("""cd $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase && grep -a "status = " output_* | grep -av "status = 0" || true """)
                        // if J is not 0, then include this text
                    }

                    if ("""$eMailID""") { 
                        /*
                        Pass and failure contxt for Github: If I and J are both zero ? Pass else Failed
                        */
                        if((env.I=="0") && (env.J=="0")) {
                            sh """
                            echo "Job is successful Because I and J are both zero. Now sending e-mail notification and cleaning workspace"
                            curl -s "https://api.GitHub.com/repos/wrf-model/WRF/statuses/$sha" \
                            -H "Content-Type: application/json" \
                            -H "Authorization: token $gitToken" \
                            -X POST \
                            -d '{"state": "success","context": "WRF-BUILD-$BUILD_NUMBER", "description": "WRF regression test is successful", "target_url": "https://ncar_jenkins.scalacomputing.com/job/WRF-Feature-Regression-Test/$BUILD_NUMBER/console"}'
                            echo "#############Job is Successful############"
                            echo "##############Sending E-Mail###############"
                            echo "Recipient is:$eMailID"
                            cd $WORKSPACE/$BUILD_NUMBER &&  unzip $WORKSPACE/$BUILD_NUMBER/wrf_output.zip
                            python $WORKSPACE/$BUILD_NUMBER/WRF/mail.py $WORKSPACE/$BUILD_NUMBER/wrf_output.zip SUCCESS $JOB_NAME $BUILD_NUMBER  $eMailID $commitID $githubuserName $pullnumber $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase/email_01.txt "$prComment" $E $F $G $H $I $J "$K" "$L" "$M" "$N" "$O" "$P"
                            echo "Cleaning workspace"
                            sudo -S rm -rfv $WORKSPACE/$BUILD_NUMBER
                            """
                        } else {
                            sh """
                            echo "Job has failed because I and J any of them are non-zero. Now sending e-mail notification and cleaning workspace"
                            curl -s "https://api.GitHub.com/repos/wrf-model/WRF/statuses/$sha" \
                            -H "Content-Type: application/json" \
                            -H "Authorization: token $gitToken" \
                            -X POST \
                            -d '{"state": "failure","context": "WRF-BUILD-$BUILD_NUMBER", "description": "WRF regression test failed", "target_url": "https://ncar_jenkins.scalacomputing.com/job/WRF-Feature-Regression-Test/$BUILD_NUMBER/console"}'
                            echo "#############Job is Successful############"
                            echo "##############Sending E-Mail###############"
                            echo "Recipient is: weiwang@ucar.edu"
                            cd $WORKSPACE/$BUILD_NUMBER && sudo -S unzip $WORKSPACE/$BUILD_NUMBER/wrf_output.zip
                            sudo -S python $WORKSPACE/$BUILD_NUMBER/WRF/mail.py $WORKSPACE/$BUILD_NUMBER/wrf_output.zip SUCCESS $JOB_NAME $BUILD_NUMBER weiwang@ucar.edu $commitID $githubuserName $pullnumber $WORKSPACE/$BUILD_NUMBER/terraform/output_testcase/email_01.txt "$prComment" $E $F $G $H $I $J "$K" "$L" "$M" "$N" "$O" "$P"
                            echo "Cleaning workspace"
                            sudo -S rm -rfv $WORKSPACE/$BUILD_NUMBER
                            sudo -S rm -rfv /tmp/raw_output_$BUILD_NUMBER
                            sudo -S rm -rfv /tmp/coop-repo_$BUILD_NUMBER
                            sudo -S rm -rfv /tmp/Success_files_$BUILD_NUMBER
                            """
                        }
                    }
                }
            }
        }

        failure {
            withCredentials([string(credentialsId: 'ncar-git-token', variable: 'gitToken')]) {
            echo "Job failed. Now sending e-mail notification and cleaning workspace"

                sh """
                curl -s "https://api.GitHub.com/repos/wrf-model/WRF/statuses/$sha" \
                -H "Content-Type: application/json" \
                -H "Authorization: token $gitToken" \
                -X POST \
                -d '{"state": "failure","context": "WRF-BUILD-$BUILD_NUMBER", "description": "WRF regression test failed.", "target_url": "https://ncar_jenkins.scalacomputing.com/job/WRF-Feature-Regression-Test/$BUILD_NUMBER/console"}'
                echo "#############Job Failed############"
                sudo -S /bin/python3.6 $WORKSPACE/$BUILD_NUMBER/WRF/SESEmailHelper.py "vlakshmanan@scalacomputing.com,kkeene@ucar.edu,weiwang@ucar.edu" "ncar-dev@scalacomputing.com" "Jenkins Build $BUILD_NUMBER with Pull request number: $pullnumber has : Status: Failed" "Jenkins build with commit id $commitID, branch name $fork_branchName by $githubuserName failed. https://ncar_jenkins.scalacomputing.com/job/WRF-Feature-Regression-Test/$BUILD_NUMBER/console" 

                echo "Cleaning workspace"
                sudo -S rm -rfv $WORKSPACE/$BUILD_NUMBER
                sudo -S rm -rfv /tmp/raw_output_$BUILD_NUMBER
                sudo -S rm -rfv /tmp/coop-repo_$BUILD_NUMBER
                sudo -S rm -rfv /tmp/Success_files_$BUILD_NUMBER
                """
            }
        }

        aborted {
/* groovylint-disable-next-line DuplicateMapLiteral */
            withCredentials([string(credentialsId: 'ncar-git-token', variable: 'gitToken')]) {
                script{
                    // if  ( readme == true || bool == true && action == '"labeled"' ||  action == '"unlabeled"' ) {
                    if (action == '"labeled"' ||  action == '"unlabeled"') {
                        echo "A label was changed"
                    } else if ( readme == true || bool == true ) {
                        echo "Change was made to a text or README file"
                    } else {
                        echo "job timed out"

                        /* groovylint-disable-next-line UnnecessaryGString */
                        echo "Job Aborted. Now sending e-mail notification and cleaning workspace"   

                        sh """
                        curl -s "https://api.GitHub.com/repos/wrf-model/WRF/statuses/$sha" \
                        -H "Content-Type: application/json" \
                        -H "Authorization: token $gitToken" \
                        -X POST \
                        -d '{"state": "success","context": "WRF-BUILD-$BUILD_NUMBER", "description": "WRF regression test not required", "target_url": "https://ncar_jenkins.scalacomputing.com/job/WRF-Feature-Regression-Test/$BUILD_NUMBER/console"}'
                        echo "#############Job Aborted############"
                        sudo -S /bin/python3.6 $WORKSPACE/$BUILD_NUMBER/WRF/SESEmailHelper.py "vlakshmanan@scalacomputing.com" "ncar-dev@scalacomputing.com" "Jenkins Build $BUILD_NUMBER with Pull request number: $pullnumber has : Status: Aborted" "Jenkins build triggered by action: $action with, commit id $commitID, branch name $fork_branchName by $githubuserName aborted because WRF regression test not required. https://ncar_jenkins.scalacomputing.com/job/WRF-Feature-Regression-Test/$BUILD_NUMBER/console"
                        echo "Cleaning workspace"
                        cd $WORKSPACE/$BUILD_NUMBER/WRF/.ci/terraform && terraform destroy -auto-approve || true
                        sudo -S rm -rfv $WORKSPACE/$BUILD_NUMBER
                        sudo -S rm -rfv /tmp/raw_output_$BUILD_NUMBER
                        sudo -S rm -rfv /tmp/coop-repo_$BUILD_NUMBER
                        sudo -S rm -rfv /tmp/Success_files_$BUILD_NUMBER
                        """
                    }
                }  
            }
        }
    }
}
