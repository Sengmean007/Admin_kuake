// Admin_weibo on Jenkins: on demand, or on a schedule.
//
// Kept in the repository rather than configured in the Jenkins UI so that a change to how the
// suites run is reviewed the same way a change to a suite is, and so a lost Jenkins can be
// rebuilt from the repository alone. The sibling UMPay suite carries its job the same way.
//
// ---------------------------------------------------------------------------------------
// SAFETY: the schedule runs the safe set only, and there is no parameter to widen it.
//
//   -DexcludedGroups=known-defect,creates-data
//
// Both exclusions are load-bearing and must not be dropped from an unattended run:
//
//   known-defect  asserts the behaviour the application *should* have, so it fails today by
//                 design. It is the regression net for open findings, not a health check.
//   creates-data  has side effects that outlive the run. In a back office that is a wide
//                 category: approving or rejecting a real transaction, creating or editing a
//                 member, locking an account, changing a setting every other suite reads.
//                 None of that belongs on a timer.
//
// EXCLUDED_GROUPS below is a parameter so a person can *narrow* it further, not widen it: the
// Test stage puts both tags back whatever is typed. Run the spending cases locally and
// attended, where you can watch what they spend. This mirrors .github/workflows/qa-suite.yml,
// which makes the same decision for the same reason.
// ---------------------------------------------------------------------------------------
//
// SETTING UP THE JOB
//
// Create a Pipeline job with *Definition: Pipeline script from SCM*, pointing at this
// repository. Nothing else needs configuring in the UI.
//
// Set **Branch Specifier** to `*/main`. This matters and is easy to miss: Jenkins defaults that
// field to `*/master`, which this repository still has but which no longer carries the work -
// main became the default branch on 2026-09-11. A job left on the default would check out a
// stale branch and build it quite happily, reporting green for code nobody is writing.
//
// Point it at a branch that does not exist and the failure is "couldn't find remote ref
// refs/heads/..." - which does not mention the field it came from.

pipeline {

    agent any

    // These names must match what is configured under Manage Jenkins -> Tools. Without this
    // block the pipeline inherits only the PATH of the account Jenkins runs as - normally
    // LocalSystem on Windows, which has no developer JDK - and the build dies on `java -version`
    // with a bare exit code 1.
    //
    // Only a JDK. Maven is NOT listed, and deliberately: this repository commits the Maven
    // wrapper, every stage below runs ./mvnw, and the wrapper pins 3.9.9 and downloads it on
    // first use. A `maven 'maven-3.9.9'` here would put a second, differently-versioned Maven
    // on the PATH that nothing uses, and the first symptom of the two disagreeing is a build
    // that behaves unlike every developer's.
    //
    // Building on a JDK newer than 17 is fine - the POM sets maven.compiler.release=17, so the
    // bytecode targets 17 whichever JDK compiles it.
    tools {
        jdk 'jdk-17'
    }

    parameters {
        string(
            name: 'SUITE',
            defaultValue: '*Test',
            description: '''Which classes Surefire scans. One of the area suites in
                            org.sengmean.suites, or one test class, or *Test for everything.
                            Passed as -Dsuite.

                            There are no suites yet - this project is a scaffold - so *Test is
                            the only value that selects anything today. List the slow ones here
                            as they are written, so whoever is picking one can tell.'''
        )
        string(
            name: 'CASE',
            defaultValue: '',
            description: '''One or more client case ids, comma separated - ADMLOGIN-001,ADMLOGIN-002.
                            Leave empty to run everything SUITE selects. Passed as -Dcase, which
                            org.sengmean.support.CaseIdFilter reads. Note -Dtest cannot do this:
                            the case id lives in the @DisplayName, and -Dtest matches method
                            names, so it would select nothing and report "Tests run: 0".'''
        )
        string(
            name: 'EXCLUDED_GROUPS',
            defaultValue: 'known-defect,creates-data',
            description: '''JUnit tags to leave out. known-defect and creates-data are added
                            back whatever is typed here - see the note at the top of this file -
                            so this can only narrow the run further, never widen it.'''
        )
        string(
            name: 'BASE_URL',
            defaultValue: '',
            description: '''Environment to run against. Empty uses Config/config.properties,
                            which is the usual answer. An origin with no path and no query
                            string; use BASE_QUERY for parameters.'''
        )
        booleanParam(
            name: 'HEADLESS',
            defaultValue: true,
            description: 'Run without a visible browser window. Agents normally have no display.'
        )
        booleanParam(
            name: 'EMAIL_REPORT',
            defaultValue: false,
            description: '''Email the report when the run finishes, as well as archiving it here.
                            Needs the firstplayright-mail-password credential to exist. Off by
                            default because Jenkins already keeps the artifacts and sends its own
                            notifications; this is for getting a result to someone outside it.'''
        )
    }

    triggers {
        // 19:00 UTC = 02:00 the next day in UTC+7, so the run lands overnight local time and the
        // results are waiting in the morning. H spreads the load rather than every job on the
        // controller firing at the same minute.
        cron('H 19 * * *')
    }

    options {
        // Generous, because the safe set is minutes rather than seconds once there are suites
        // in it. Lower it if it ever starts masking a hang.
        timeout(time: 90, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '10'))
        timestamps()
        // Two runs at once would sign in as the same operator from two browsers and act on the
        // same records in both, which produces failures that look like application bugs and
        // are not.
        disableConcurrentBuilds()
    }

    environment {
        // Where Playwright keeps its browsers. Pinned to a cache beside JENKINS_HOME so a fresh
        // agent downloads them once rather than once per build.
        PLAYWRIGHT_BROWSERS_PATH = "${env.JENKINS_HOME}/.cache/ms-playwright"
    }

    stages {

        stage('Checkout') {
            steps {
                // checkout scm rather than naming the repository again: it takes the branch, the
                // URL and the credentials from the job's own SCM configuration, and checks out
                // the very revision this Jenkinsfile was read from. Spelling it out a second
                // time means the branch is configured in two places that can disagree, and the
                // build failure that produces says nothing about there being two settings.
                checkout scm
            }
        }

        stage('Tools') {
            steps {
                // Fails early and legibly if the agent is missing something, rather than
                // halfway through a suite that has already opened a browser.
                script {
                    String wrapper = isUnix() ? './mvnw -v' : 'mvnw.cmd -v'
                    for (check in ['java -version', wrapper]) {
                        int code = isUnix()
                                ? sh(script: check, returnStatus: true)
                                : bat(script: check, returnStatus: true)

                        if (code != 0) {
                            error("This agent cannot run '${check}' (exit ${code}). The JDK comes "
                                + "from the tools block, so check its name matches what is "
                                + "configured under Manage Jenkins -> Tools. The Maven wrapper "
                                + "is in the repository and needs only network access on its "
                                + "first run, to download Maven 3.9.9 itself.")
                        }
                    }
                }
            }
        }

        stage('Browsers') {
            steps {
                // Playwright downloads its browsers by itself on first use, but it does not
                // install the OS packages Chromium needs in order to start, and that failure
                // surfaces much later as a launch error inside a test. Doing it here means the
                // agent problem is reported as an agent problem.
                //
                // Not `mvn exec:java`: the exec plugin in this POM has mainClass pinned to the
                // Excel report generator at plugin level, and a plugin-level <configuration>
                // beats -Dexec.mainClass - so that route silently runs the report generator.
                // The classpath is built out instead and the CLI invoked directly.
                script {
                    if (isUnix()) {
                        sh '''
                            ./mvnw -B -ntp test-compile
                            ./mvnw -B -ntp -q \
                              org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath \
                              -Dmdep.outputFile=cp.txt
                            java -cp "$(cat cp.txt)" com.microsoft.playwright.CLI install --with-deps chromium
                        '''
                    } else {
                        // --with-deps is a Linux-only flag; on Windows the browser is all that
                        // is needed, because the libraries it links against are already there.
                        bat '''
                            call mvnw.cmd -B -ntp test-compile || exit /b 1
                            call mvnw.cmd -B -ntp -q org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath -Dmdep.outputFile=cp.txt || exit /b 1
                            set /p CP=<cp.txt
                            java -cp "%CP%" com.microsoft.playwright.CLI install chromium
                        '''
                    }
                }
            }
        }

        stage('Test') {
            steps {
                script {
                    // Whatever was typed, both safety tags go back in. See the note at the top:
                    // this parameter exists to narrow a run, not to widen one.
                    Set<String> excluded = new LinkedHashSet<>()
                    excluded.addAll(['known-defect', 'creates-data'])
                    params.EXCLUDED_GROUPS?.split(',').each { tag ->
                        if (tag?.trim()) {
                            excluded.add(tag.trim())
                        }
                    }

                    List<String> goals = ['-B', '-ntp', 'clean', 'test']
                    goals << "-Dsuite=${params.SUITE?.trim() ?: '*Test'}"
                    goals << "-Dheadless=${params.HEADLESS}"
                    goals << "-DexcludedGroups=${excluded.join(',')}"

                    if (params.CASE?.trim()) {
                        goals << "-Dcase=${params.CASE.trim()}"
                    }
                    if (params.BASE_URL?.trim()) {
                        goals << "-Dbase.url=${params.BASE_URL.trim()}"
                    }
                    goals << "-Dmail.enabled=${params.EMAIL_REPORT}"

                    // maven.test.failure.ignore keeps Maven's exit code about the BUILD rather
                    // than about the tests. Without it Surefire exits 1 on any failing case, the
                    // stage fails, and Jenkins calls the whole build FAILURE - which reads as
                    // "the job is broken" when the job worked perfectly and three cases did not
                    // pass. The junit step in post reads the results and marks the build
                    // unstable instead, so a real problem - a compile error, a missing tool -
                    // still fails, and a test failure stops pretending to be one.
                    goals << '-Dmaven.test.failure.ignore=true'

                    // Credentials only when they will be used, so a job without the credential
                    // configured still runs rather than failing at the binding.
                    if (params.EMAIL_REPORT) {
                        withCredentials([string(credentialsId: 'firstplayright-mail-password',
                                                variable: 'MAIL_PASSWORD')]) {
                            // MAIL_PASSWORD, not -Dmail.password: a -D lands in the process
                            // arguments, where anything on the agent can read it and where
                            // Jenkins' own log masking does not reach. ConfigDataProvider reads
                            // the environment variable of the same name, which is why the
                            // convention exists.
                            runMaven(goals)
                        }
                    } else {
                        runMaven(goals)
                    }
                }
            }
        }
    }

    post {

        always {
            // Gives Jenkins its test trend, and is what lets a build be unstable rather than
            // simply failed.
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'

            // The client-facing reports: one page per suite, the index, the Extent dashboard and
            // the screenshots under each. Written during the run by HtmlReportListener and
            // ExtentReportListener, pass or fail, so they are already on disk and already
            // describe this run - there is nothing to rebuild here.
            archiveArtifacts artifacts: 'docs/reports/**',
                             allowEmptyArchive: true,
                             fingerprint: false

            // ScreenshotOnFinish writes one per case. They are what makes a failed run
            // diagnosable afterwards, when the message says an element was not found but not
            // what was on the screen instead.
            archiveArtifacts artifacts: 'target/case-screenshots/**',
                             allowEmptyArchive: true
        }

        unstable {
            echo 'Cases failed, but the job itself ran fine. These are integration tests against ' +
                 'a live third-party site: they fail for reasons unrelated to any commit, and ' +
                 'roughly half of fresh sign-ins against this environment land unauthenticated. ' +
                 'A red run is a signal to look, not proof of a regression. Open ' +
                 'docs/reports/index.html from the artifacts before treating one as a bug.'
        }

        failure {
            echo 'The build failed rather than the cases - Maven did not get as far as a result. ' +
                 'Usually the agent: a JDK name that does not match Manage Jenkins -> Tools, no ' +
                 'network for the Maven wrapper on its first run, or a compile error.'
        }
    }
}

/** Runs the wrapper, so the goal list is assembled once rather than once per platform. */
void runMaven(List<String> goals) {
    String arguments = goals.join(' ')
    if (isUnix()) {
        sh "./mvnw ${arguments}"
    } else {
        bat "mvnw.cmd ${arguments}"
    }
}
