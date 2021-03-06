package org.springframework.jenkins.cloud.e2e

import org.springframework.jenkins.cloud.common.SpringCloudJobs
import javaposse.jobdsl.dsl.DslFactory
import org.springframework.jenkins.cloud.common.SpringCloudNotification
import org.springframework.jenkins.common.job.Cron
import org.springframework.jenkins.common.job.JdkConfig
import org.springframework.jenkins.common.job.Label
import org.springframework.jenkins.common.job.TestPublisher

/**
 * @author Marcin Grzejszczak
 */
class SpringCloudSamplesEndToEndBuildMaker implements TestPublisher,
		JdkConfig, BreweryDefaults, Label, Cron, SpringCloudJobs {

	private final DslFactory dsl
	private final String organization

	SpringCloudSamplesEndToEndBuildMaker(DslFactory dsl) {
		this.dsl = dsl
		this.organization = "spring-cloud-samples"
	}

	SpringCloudSamplesEndToEndBuildMaker(DslFactory dsl, String organization) {
		this.dsl = dsl
		this.organization = organization
	}

	void build(String projectName, String cronExpr) {
		build(projectName, projectName, "scripts/runAcceptanceTests.sh", cronExpr)
	}

	void buildWithGradleTests(String projectName, String branch, String cronExpr) {
		build(projectName, projectName, "scripts/runAcceptanceTests.sh", cronExpr, branch, '', false, true)
	}

	void buildWithMavenTests(String projectName, String branch, String cronExpr) {
		build(projectName, projectName, "scripts/runAcceptanceTests.sh", cronExpr, branch, '', true, false)
	}

	void buildWithoutTests(String projectName, String branch, String cronExpr) {
		build(projectName, projectName, "scripts/runAcceptanceTests.sh", cronExpr, branch, "", false, false)
	}

	void buildWithoutTests(String projectName, String cronExpr) {
		build(projectName, projectName, "scripts/runAcceptanceTests.sh", cronExpr, masterBranch(), "", false, false)
	}

	void buildWithGradleAndMavenTests(String projectName, String cronExpr, String branch = masterBranch()) {
		build(projectName, projectName, "scripts/runAcceptanceTests.sh", cronExpr, branch, '', true, true)
	}

	protected void build(String projectName, String repoName, String scriptName, String cronExpr, String branchName = masterBranch(),
						 String postBuildScripts = "", boolean mavenTests = false,
						 boolean gradleTests = false, boolean withNodeJs = false) {
		String organization = this.organization
		dsl.job("${prefixJob(projectName)}-${branchName}-e2e") {
			triggers {
				cron cronExpr
			}
			jdk jdk8()
			wrappers {
				timestamps()
				colorizeOutput()
				environmentVariables([
						KILL_AT_THE_END: 'yes',
						TERM: 'dumb',
						RETRIES: 70
				])
				timeout {
					noActivity(defaultInactivity())
					failBuild()
					writeDescription('Build failed due to timeout after {0} minutes of inactivity')
				}
				if (withNodeJs) {
					nodejs("9.11.1 Latest Stable")
				}
			}
			scm {
				git {
					remote {
						url "https://github.com/${organization}/$repoName"
						branch branchName
					}
					extensions {
						wipeOutWorkspace()
					}
				}
			}
			steps {
				shell("""#!/bin/bash
						./${scriptName}
					""")
				if (postBuildScripts) {
					shell("""#!/bin/bash
						./${postBuildScripts}
					""")
				}
			}
			configure {
				SpringCloudNotification.cloudSlack(it as Node)
			}
			publishers {
				if (gradleTests) {
					archiveJunit gradleJUnitResults()
				}
				if (mavenTests) {
					archiveJunit '**/*-reports/*.xml'
				}
			}
		}
	}
}


