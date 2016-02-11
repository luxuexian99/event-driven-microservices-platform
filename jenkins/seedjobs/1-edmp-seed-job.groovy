import groovy.json.JsonSlurper
import hudson.FilePath
import hudson.*

println "############################################################################################################"
println "Reading project configuration from json"

hudson.FilePath workspace = hudson.model.Executor.currentExecutor().getCurrentWorkspace()
File file = new File("${workspace}/jenkins/seedjobs/1-edmp-project-configuration.json")
def slurper = new JsonSlurper()
def jsonText = file.getText()
projects = slurper.parseText( jsonText )

println "############################################################################################################"
println "Iterating all projects"
println ""

projects.each {
  println "############################################################################################################"
  println ""
  println "Creating Jenkins Jobs for Git Project: ${it.gitProjectName}"
  println "- gitRepositoryUrl=${it.gitRepositoryUrl}"
  println "- rootWorkDirectory=${it.rootWorkDirectory}"
  println ""

  createCIJob(it.gitProjectName, it.gitRepositoryUrl, it.rootWorkDirectory)

}

def createCIJob(def gitProjectName, def gitRepositoryUrl, def rootWorkDirectory) {

  println "############################################################################################################"
  println "Creating CI Job:"
  println "- gitProjectName     = ${gitProjectName}"
  println "- gitRepositoryUrl   = ${gitRepositoryUrl}"
  println "- rootWorkDirectory  = ${rootWorkDirectory}"
  println "############################################################################################################"

  def jobName = "${gitProjectName}-1-ci"
  if( rootWorkDirectory.size() > 0 ) {
    jobName = "${gitProjectName}-${rootWorkDirectory}-1-ci"
  }

  job(jobName) {
    parameters {
      stringParam("BRANCH", "master", "Define TAG or BRANCH to build from")
      stringParam("REPOSITORY_URL", "http://\${EVENTDRIVENMICROSERVICESPLATFORM_NEXUS_1_PORT_8081_TCP_ADDR}:\${EVENTDRIVENMICROSERVICESPLATFORM_NEXUS_1_PORT_8081_TCP_PORT}/nexus/content/repositories/releases/", "Nexus Release Repository URL")
    }
    scm {
      git {
        remote {
          url(gitRepositoryUrl)
        }
        createTag(false)
        clean()
      }
    }
    wrappers {
      colorizeOutput()
      preBuildCleanup()
    }
    triggers {
      scm('30/H * * * *')
      githubPush()
    }
    steps {
      maven {
          goals('clean versions:set -DnewVersion=\${BUILD_NUMBER}')
          mavenInstallation('Maven 3.3.3')
          rootPOM("${rootWorkDirectory}/pom.xml")
          mavenOpts('-Xms512m -Xmx1024m')
          providedGlobalSettings('MyGlobalSettings')
      }
      maven {
        goals('clean deploy')
        mavenInstallation('Maven 3.3.3')
        rootPOM("${rootWorkDirectory}/pom.xml")
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('MyGlobalSettings')
      }
    }
    publishers {
      chucknorris()
      archiveJunit('**/target/surefire-reports/*.xml')
      publishCloneWorkspace('**', '', 'Any', 'TAR', true, null)
      /**
      downstreamParameterized {
        trigger("${gitProjectName}-app-2-sonar") {
          currentBuild()
        }
      }
      */
    }
  }
}

def createSonarJob(def gitProjectName, def gitRepositoryUrl, def rootWorkDirectory) {

  println "############################################################################################################"
  println "Creating Sonar Job:"
  println "- gitProjectName     = ${gitProjectName}"
  println "- gitRepositoryUrl   = ${gitRepositoryUrl}"
  println "- rootWorkDirectory  = ${rootWorkDirectory}"
  println "############################################################################################################"

  job("conference-app-2-sonar") {
    parameters {
      stringParam("BRANCH", "master", "Define TAG or BRANCH to build from")
    }
    scm {
      cloneWorkspace("conference-app-1-ci")
    }
    wrappers {
      colorizeOutput()
      preBuildCleanup()
    }
    steps {
      maven {
        goals('org.jacoco:jacoco-maven-plugin:0.7.4.201502262128:prepare-agent install -Psonar')
        mavenInstallation('Maven 3.3.3')
        rootPOM('app/pom.xml')
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('MyGlobalSettings')
      }
      maven {
        goals('sonar:sonar -Psonar')
        mavenInstallation('Maven 3.3.3')
        rootPOM('app/pom.xml')
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('MyGlobalSettings')
      }
    }
    publishers {
      chucknorris()
    }
  }
}