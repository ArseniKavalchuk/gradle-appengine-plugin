/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gradle.test.integration

import org.apache.commons.io.FileUtils
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/**
 * Base class for integration tests involving an test application (pulled from resources)
 *
 * TODO : consider turning this into a reusable fixture
 *
 * @author Appu Goundan
 */
abstract class AppIntegrationTest {

    protected final String APP_BASE_DIR = "projects"
    protected File projectRoot

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder()

    /** Create the temp directory to copy the project and run gradle on */
    @Before
    void setUp() {
        projectRoot = tempFolder.getRoot()
        FileUtils.copyDirectory(new File(getClass().getClassLoader().getResource(getAppResourceBaseDir()).toURI()), projectRoot)
        replaceVersions()
        String[] tasksToRun = getPreTestTasks()
        if (tasksToRun != null && tasksToRun.length > 0) {
            runOnProject { ProjectConnection connection ->
                connection.newBuild().forTasks(tasksToRun).run()
            }
        }
    }

    protected void runOnProject(Closure connectionClosure) {
        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(projectRoot)
        configureGradleConnector(connector)
        ProjectConnection connection = connector.connect()
        try {
            connectionClosure(connection)
        }
        finally {
            connection.close()
        }

    }

    /** Used to replace version in gradle build files to current App Engine version **/
    protected replaceVersions() {
        new File(projectRoot, "build.gradle").withWriter { w ->
            new File(projectRoot, getBuildGradleTemplateName()).eachLine { line ->
                w << line
                        .replaceAll("@@pluginversion@@", System.getProperty("appengine.pluginversion"))
                        .replaceAll("@@version@@", System.getProperty("appengine.version"))
                        .replaceAll("@@testrepo@@", System.getProperty("test.maven.repo"))
                w << "\n"
            }
        }
    }

    /** Override this if a test project has multiple build.gradle templates but a single project source,
     * see replaceVersions() **/
    protected String getBuildGradleTemplateName() {
        return "build.gradle.template"
    }

    /** Name of the test project directory, subclasses implement this to copy the correct project **/
    abstract protected String getTestAppName()

    /** Location of the App Engine project to test against **/
    protected String getAppResourceBaseDir() {
        APP_BASE_DIR + File.separatorChar + getTestAppName()
    }

    /** List of tasks to run during setup **/
    protected String[] getPreTestTasks() {[]}

    /** optional connector configuration **/
    protected void configureGradleConnector(GradleConnector connector) {}

}
