package com.electriccloud.plugins

import org.gradle.api.Project;
import org.gradle.api.Plugin;

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory
import javax.xml.xpath.XPathConstants
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.TransformerFactory

class BuildPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		project.afterEvaluate {
			project.tasks.deploy.dependsOn('jar')
			project.tasks.deploy.setDescription('Deploys plugin on Commander server')
		}
		
		if(!project.getTasksByName('processProjectXml', true).size()) {
			project.task('processProjectXml') << {
				def resourcesPath = "${project.buildDir}/resources/main"
				def projectPath = "$resourcesPath/project"

				def xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				def xmlp = XPathFactory.newInstance().newXPath()

				def projectXml = xml.parse("$projectPath/project.xml")
				def replacements = xml.parse("$projectPath/manifest.xml")

				/* For each file entry in manifest, replace content of node
				 * located by xpath expression with file content
				 */
				xmlp.evaluate("//file", replacements.documentElement, XPathConstants.NODESET).each {
					def file = "$projectPath/${xmlp.evaluate('path', it)}"
					def xpath = xmlp.evaluate('xpath', it)
					def nodes = xmlp.evaluate(xpath, projectXml.documentElement, XPathConstants.NODESET)

					nodes.each { it.setTextContent(new File(file).text) }
				}

				def source = new DOMSource(projectXml)
				def result = new StreamResult(new File("$resourcesPath/META-INF/project.xml"))
				def transformer = TransformerFactory.newInstance().newTransformer()

				transformer.transform(source, result)
			}
		}

		if(!project.getTasksByName('deploy', true).size()) {
			project.task('deploy') << {
				def ectool = project.commanderHome ? "${project.commanderHome}/bin/ectool" : "ectool"
				def serverOpt = "--server ${project.commanderServer}"

				project.exec {
					commandLine ectool, serverOpt, 'login', project.commanderUser, project.commanderPassword
				}

				project.exec {
					commandLine ectool, serverOpt, 'installPlugin', jar.archivePath
				}

				exec {
					commandLine ectool, serverOpt, 'promotePlugin', project.pluginName
				}
			}
		}

		project.configure(project) {
			apply plugin: 'java'
			apply plugin: 'gwt-compiler'

			
			repositories {
				mavenCentral()
				flatDir { dirs 'libs' }
			}

			dependencies {
				compile group: project.group, name: "commander-sdk", version: "5.0.0-SNAPSHOT"
				compile group: project.group, name: "commander-client", version: "5.0.0-SNAPSHOT"
				compile group: project.group, name: "ec_internal", version: "5.0.0-SNAPSHOT"
				compile group: project.group, name: "ec-test", version: "5.0.0-SNAPSHOT"
				compile group: "com.google.gwt", name: "gwt-dev", version: "2.5.0"
				compile group: "com.google.gwt", name: "gwt-servlet", version: "2.5.0"
				compile group: "com.google.gwt", name: "gwt-user", version: "2.5.0"
				compile "com.intellij:annotations:132.839-PATCH1"
				compile "com.google.guava:guava-gwt:16.+"
				compile "com.google.gwt.inject:gin:1.5.0"
				compile "com.gwtplatform:gwtp-all:0.8-PATCH5"
			}

			processResources {
				from(projectDir, {
					include 'agent/**'
					include 'htdocs/**'
					include 'cgi-bin/**'
					include 'pages/**'
				})

				/* Apply macros substitution only on subset of files.
				 * This is to ensure, that no binary resources is corrupted by filter
				 */
				[
					"**/*.xml",
					"**/*.css",
					"**/*.pm",
					"**/*.pl",
					"**/*.cgi"
				].each {
					filesMatching(it, {
						filter(org.apache.tools.ant.filters.ReplaceTokens, tokens: [
							PLUGIN_NAME: project.pluginName.toString(),
							PLUGIN_KEY: project.name.toString(),
							PLUGIN_VERSION: project.version.toString()])
					})
				}
			}

			jar {
				dependsOn = [
					processProjectXml,
					compileGwt
				]

				doFirst { println "Building plugin jar: $archiveName" }

				manifest {
					attributes (
							'Implementation-Vendor': 'Electric Cloud, Inc.',
							'Implementation-Title': project.name,
							'Implementation-Version': project.version,
							'Implementation-Vendor-Id': project.group
							)
				}

				outputs.upToDateWhen { false }
				appendix = "plugin"
				destinationDir = buildDir
				includeEmptyDirs = false
				excludes = [
					'WEB-INF/**',
					'ecplugins/**',
					'project/**',
					'**/*.pl',
					'**/*.pm'
				]

				from sourceSets.main.output
				from (tasks.compileGwt.outputs, { into('htdocs/war') })
			}

			ext {
				pluginName = "$name-$version"
				buildNumber = System.env.BUILD_NUMBER ? System.env.BUILD_NUMBER : '0'
				commanderHome = System.env.COMMANDER_HOME
				commanderServer = System.env.COMMANDER_SERVER ? System.env.COMMANDER_SERVER : 'localhost'
				commanderUser = System.env.COMMANDER_USER ? System.env.COMMANDER_USER : 'admin'
				commanderPassword = System.env.COMMANDER_PASSWORD ? System.env.COMMANDER_PASSWORD : 'changeme'
			}

			gwt {
				/**
				 *  Setup reasonable memory defaults for GWT compiler
				 */
				minHeapSize '512M'
				maxHeapSize '1024M'
				gwtVersion '2.7.0'
			}
		}
	}
}