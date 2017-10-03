package org.springframework.jenkins.cloud.release

import javaposse.jobdsl.dsl.DslFactory

import org.springframework.jenkins.cloud.common.SpringCloudJobs
import org.springframework.jenkins.cloud.common.SpringCloudNotification
import org.springframework.jenkins.common.job.Cron
import org.springframework.jenkins.common.job.JdkConfig
import org.springframework.jenkins.common.job.Maven
import org.springframework.jenkins.common.job.TestPublisher

/**
 * @author Marcin Grzejszczak
 */
class SpringCloudReleaseMaker implements JdkConfig, TestPublisher,
		SpringCloudJobs {
	private static final String RELEASER_POM_BRANCH_VAR = "RELEASER_POM_BRANCH"
	private final DslFactory dsl
	final String organization

	SpringCloudReleaseMaker(DslFactory dsl) {
		this.dsl = dsl
		this.organization = 'spring-cloud'
	}

	SpringCloudReleaseMaker(DslFactory dsl, String organization) {
		this.dsl = dsl
		this.organization = organization
	}

	void release(String project) {
		dsl.job("$project-releaser") {
			parameters {
				stringParam(branchVarName(), masterBranch(), "Your project's branch")
				stringParam(RELEASER_POM_BRANCH_VAR, masterBranch(), 'Spring Cloud Release branch')
			}
			jdk jdk8()
			scm {
				git {
					remote {
						url "https://github.com/${organization}/${project}"
						branch "\$${branchVarName()}"
						credentials(githubUserCredentialId())
					}
					extensions {
						wipeOutWorkspace()
					}
				}
			}
			wrappers {
				timestamps()
				colorizeOutput()
				maskPasswords()
				credentialsBinding {
					file(gpgSecRing(), "spring-signing-secring.gpg")
					file(gpgPubRing(), "spring-signing-pubring.gpg")
					string(gpgPassphrase(), "spring-gpg-passphrase")
					string(githubToken(), githubTokenCredId())
					usernamePassword(sonatypeUser(), sonatypePassword(), "oss-token")
				}
				timeout {
					noActivity(300)
					failBuild()
					writeDescription('Build failed due to timeout after {0} minutes of inactivity')
				}
			}
			steps {
				// build the releaser
				shell("""#!/bin/bash
				echo "Cloning and building the releaser"
				rm -rf .git/releaser && git clone -b master --single-branch https://github.com/spring-cloud/spring-cloud-release-tools.git .git/releaser
				pushd .git/releaser
				./mvnw clean install
				popd
				""")
				// run the releaser against the project
				shell("""#!/bin/bash
				${setupGitCredentials()}
				set +x
				SYSTEM_PROPS="-Dgpg.secretKeyring="\$${gpgSecRing()}" -Dgpg.publicKeyring="\$${gpgPubRing()}" -Dgpg.passphrase="\$${gpgPassphrase()}" -DSONATYPE_USER="\$${sonatypeUser()}" -DSONATYPE_PASSWORD="\$${sonatypePassword()}""
				java -jar .git/releaser/spring-cloud-release-tools-spring/target/spring-cloud-release-tools-spring-1.0.0.BUILD-SNAPSHOT.jar --releaser.pom.branch=\${$RELEASER_POM_BRANCH_VAR} --spring.config.name=releaser --releaser.maven.system-properties="\${SYSTEM_PROPS}" --full-release --interactive=false || (${cleanGitCredentials()} && exit 1)
				""")
			}
			configure {
				SpringCloudNotification.cloudSlack(it as Node)
			}
			publishers {
				archiveJunit mavenJUnitResults()
			}
		}
	}

	private String gpgSecRing() {
		return 'FOO_SEC'
	}

	private String gpgPubRing() {
		return 'FOO_PUB'
	}

	private String gpgPassphrase() {
		return 'FOO_PASSPHRASE'
	}

	private String sonatypeUser() {
		return 'SONATYPE_USER'
	}

	private String sonatypePassword() {
		return 'SONATYPE_PASSWORD'
	}

	private String githubToken() {
		return 'RELEASER_GIT_OAUTH_TOKEN'
	}

	private String githubTokenCredId() {
		return '7b3ebbea-7001-479b-8578-b8c464dab973'
	}
}