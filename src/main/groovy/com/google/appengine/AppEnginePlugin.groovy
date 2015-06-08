/*
 * Copyright 2011 the original author or authors.
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
package com.google.appengine

import com.google.appengine.tooling.AppEngineToolingBuilderModel
import com.google.appengine.task.DownloadSdkTask
import com.google.appengine.task.EnhanceTask
import com.google.appengine.task.ExplodeAppTask
import com.google.appengine.task.RunTask
import com.google.appengine.task.StopTask
import com.google.appengine.task.WebAppDirTask
import com.google.appengine.task.endpoints.ClientLibProcessingTask
import com.google.appengine.task.endpoints.EndpointsTask
import com.google.appengine.task.endpoints.ExpandClientLibsTask
import com.google.appengine.task.endpoints.ExportClientLibsTask
import com.google.appengine.task.endpoints.GetClientLibsTask
import com.google.appengine.task.endpoints.GetDiscoveryDocsTask
import com.google.appengine.task.endpoints.InstallClientLibsTask
import com.google.appengine.util.VersionComparator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.plugins.*
import com.google.appengine.task.appcfg.*
import com.google.appengine.task.appcfg.backends.*
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.plugins.ear.EarPluginConvention
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.tooling.BuildException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject

import static org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME

/**
 * <p>A {@link Plugin} that provides tasks for uploading, running and managing of Google App Engine projects.</p>
 *
 * @author Benjamin Muschko
 * @author Matt Stephenson
 */
class AppEnginePlugin implements Plugin<Project> {

    // TODO: eclipse plugin integration has special code for v2.3, fix it when ready
    static final String GRADLE_MIN_VERSION = '2.1'

    static final String APPENGINE_SDK_CONFIGURATION_NAME = 'appengineSdk'
    static final String APPENGINE_GROUP = 'Google App Engine'
    static final String APPENGINE_EXPERIMENTAL_GROUP = 'Google App Engine Experimental'
    static final String APPENGINE_DOWNLOAD_SDK = 'appengineDownloadSdk'
    static final String APPENGINE_RUN = 'appengineRun'
    static final String APPENGINE_STOP = 'appengineStop'
    static final String APPENGINE_ENHANCE = 'appengineEnhance'
    static final String APPENGINE_UPDATE = 'appengineUpdate'
    static final String APPENGINE_ROLLBACK = 'appengineRollback'
    static final String APPENGINE_UPDATE_INDEXES = 'appengineUpdateIndexes'
    static final String APPENGINE_VACUUM_INDEXES = 'appengineVacuumIndexes'
    static final String APPENGINE_UPDATE_TASK_QUEUES = 'appengineUpdateQueues'
    static final String APPENGINE_UPDATE_DISPATCH = 'appengineUpdateDispatch'
    static final String APPENGINE_UPDATE_DOS = 'appengineUpdateDos'
    static final String APPENGINE_UPDATE_CRON = 'appengineUpdateCron'
    static final String APPENGINE_CRON_INFO = 'appengineCronInfo'
    static final String APPENGINE_LOGS = 'appengineLogs'
    static final String APPENGINE_VERSION = 'appengineVersion'
    static final String APPENGINE_DOWNLOAD_APP = 'appengineDownloadApp'
    static final String APPENGINE_EXPLODE_WAR = 'appengineExplodeApp'
    static final String APPENGINE_UPDATE_BACKEND = 'appengineUpdateBackend'
    static final String APPENGINE_UPDATE_ALL_BACKENDS = 'appengineUpdateAllBackends'
    static final String APPENGINE_ROLLBACK_BACKEND = 'appengineRollbackBackend'
    static final String APPENGINE_LIST_BACKENDS = 'appengineListBackends'
    static final String APPENGINE_START_BACKEND = 'appengineStartBackend'
    static final String APPENGINE_STOP_BACKEND = 'appengineStopBackend'
    static final String APPENGINE_DELETE_BACKEND = 'appengineDeleteBackend'
    static final String APPENGINE_CONFIGURE_BACKENDS = 'appengineConfigureBackends'
    static final String APPENGINE_UPDATE_ALL = 'appengineUpdateAll'
    static final String APPENGINE_FUNCTIONAL_TEST = 'appengineFunctionalTest'
    static final String APPENGINE_ENDPOINTS_GET_DISCOVERY_DOCS = "appengineEndpointsGetDiscoveryDocs"
    static final String APPENGINE_ENDPOINTS_GET_CLIENT_LIBS = "appengineEndpointsGetClientLibs"
    static final String APPENGINE_ENDPOINTS_EXPAND_CLIENT_LIBS = "appengineEndpointsExpandClientLibs"
    static final String APPENGINE_ENDPOINTS_EXPORT_CLIENT_LIBS = "appengineEndpointsExportClientLibs"
    static final String APPENGINE_ENDPOINTS_INSTALL_CLIENT_LIBS = "appengineEndpointsInstallClientLibs"
    static final String GRADLE_USER_PROP_PASSWORD = 'appenginePassword'
    static final String EXPLODED_WAR_DIR_CONVENTION_PARAM = 'explodedAppDirectory'
    static final String EXPLODED_SDK_DIR_CONVENTION_PARAM = 'explodedSdkDirectory'
    static final String ENDPOINTS_CLIENT_LIB_CONVENTION_PARAM = "clientLibDirectory"
    static final String ENDPOINTS_DISCOVERY_DOC_CONVENTION_PARAM = "discoveryDocDirectory"
    static final String ENDPOINTS_DISCOVERY_DOC_FORMAT_PARAM = "discoveryDocFormat"
    static final String ENDPOINTS_CLIENT_LIB_COPY_JAR_CONVENTION_PARAM = "clientLibJarOut"
    static final String ENDPOINTS_CLIENT_LIB_COPY_SRC_JAR_CONVENTION_PARAM = "clientLibSrcJarOut"
    static final String BACKEND_PROJECT_PROPERTY = 'backend'
    static final String SETTING_PROJECT_PROPERTY = 'setting'
    static final String FUNCTIONAL_TEST_COMPILE_CONFIGURATION = 'functionalTestCompile'
    static final String FUNCTIONAL_TEST_RUNTIME_CONFIGURATION = 'functionalTestRuntime'
    static final String FUNCTIONAL_TEST_SOURCE_SET = 'functionalTest'

    private final ToolingModelBuilderRegistry registry
    @Inject
    public AppEnginePlugin(ToolingModelBuilderRegistry registry) {
        this.registry = registry
    }

    private File explodedSdkDirectory
    private File explodedAppDirectory
    private File stagedAppDirectory
    private File downloadedAppDirectory
    private File discoveryDocDirectory
    private File endpointsClientLibDirectory
    private File endpointsExpandedSrcDirectory
    
    private AppEnginePluginExtension appEnginePluginExtension

    @Override
    void apply(Project project) {
        checkGradleVersion(project)
        this.registry.register(new AppEngineToolingBuilderModel())
        project.configurations.create(APPENGINE_SDK_CONFIGURATION_NAME).setVisible(false).setTransitive(true)
                .setDescription('The Google App Engine SDK to be downloaded and used for this project.')

        this.appEnginePluginExtension = project.extensions.create('appengine', AppEnginePluginExtension, project)

        this.explodedSdkDirectory = getExplodedSdkDirectory(project)
        this.explodedAppDirectory = getExplodedAppDirectory(project)
        this.stagedAppDirectory = getStagedAppDirectory(project)
        this.downloadedAppDirectory = getDownloadedAppDirectory(project)
        this.discoveryDocDirectory = getDiscoveryDocDirectory(project)
        this.endpointsClientLibDirectory = getEndpointsClientLibDirectory(project)
        this.endpointsExpandedSrcDirectory = getEndpointsExpandedSrcDir(project)
        configureDownloadSdk(project)
        configureWebAppDir(project)
        configureWorkingDirectory(project)
        configureAppConfig(project)
        configureExplodeWarTask(project)
        configureStageAppTask(project)
        configureRun(project)
        configureStop(project)
        configureUpdate(project)
        configureRollback(project)
        configureSdk(project)
        configureEnhance(project)
        configureUpdateIndexes(project)
        configureVacuumIndexes(project)
        configureUpdateTaskQueues(project)
        configureUpdateDispatch(project)
        configureUpdateDoS(project)
        configureUpdateCron(project)
        configureCronInfo(project)
        configureDownloadLogs(project)
        configureVersion(project)
        configureDownloadApplication(project)
        configureSingleBackendTask(project)
        configureUpdateBackends(project)
        configureUpdateAllBackends(project)
        configureRollbackBackends(project)
        configureListBackends(project)
        configureStartBackend(project)
        configureStopBackend(project)
        configureDeleteBackend(project)
        configureConfigureBackends(project)
        configureUpdateAll(project)
        configureEndpoints(project)
        configureFunctionalTest(project)
    }

    static void checkGradleVersion(Project project) {
        String projectGradleVersion = project.gradle.gradleVersion
        if(VersionComparator.compare(GRADLE_MIN_VERSION, projectGradleVersion) > 0) {
            throw new BuildException(String.format(
                    "Detected Gradle version %s, but the gradle-appengine-plugin requires Gradle version %s or higher.",
                    projectGradleVersion, GRADLE_MIN_VERSION), null)
        }
    }

    static File getExplodedSdkDirectory(Project project) {
        new File(project.gradle.gradleUserHomeDir, 'appengine-sdk')
    }

    static File getExplodedAppDirectory(Project project) {
        getBuildSubDirectory(project, 'exploded-app')
    }

    static File getStagedAppDirectory(Project project) {
        getBuildSubDirectory(project, "staged-app")
    }

    static File getDownloadedAppDirectory(Project project) {
        getBuildSubDirectory(project, 'downloaded-app')
    }

    static File getDiscoveryDocDirectory(Project project) {
        getBuildSubDirectory(project, 'discovery-docs')
    }

    static File getEndpointsClientLibDirectory(Project project) {
        getBuildSubDirectory(project, 'client-libs')
    }

    static File getGenDir(Project project) {
        getBuildSubDirectory(project, 'generated-source')
    }

    static File getEndpointsExpandedSrcDir(Project project) {
        new File(getGenDir(project),'endpoints/java')
    }

    static File getBuildSubDirectory(Project project, String subDirectory) {
        def subDir = new StringBuilder()
        subDir <<= project.buildDir
        subDir <<= System.getProperty('file.separator')
        subDir <<= subDirectory
        new File(subDir.toString())
    }

    private void configureDownloadSdk(Project project) {
        project.tasks.withType(DownloadSdkTask).whenTaskAdded { DownloadSdkTask appengineDownloadSdkTask ->
            appengineDownloadSdkTask.conventionMapping.map('appengineSdkZipFile') {
                try {
                    project.configurations.getByName(APPENGINE_SDK_CONFIGURATION_NAME).singleFile
                }
                catch(IllegalStateException e) {
                    // make "gradle tasks" happy in case we don't declare configuration!
                }
            }
            appengineDownloadSdkTask.conventionMapping.map(EXPLODED_SDK_DIR_CONVENTION_PARAM) { this.explodedSdkDirectory }
        }

        DownloadSdkTask appengineDownloadSdkTask = project.tasks.create(APPENGINE_DOWNLOAD_SDK, DownloadSdkTask)
        appengineDownloadSdkTask.description = 'Downloads and sets Google App Engine SDK.'
        appengineDownloadSdkTask.group = APPENGINE_GROUP
    }

    private void configureWebAppDir(Project project) {
        project.tasks.withType(WebAppDirTask).whenTaskAdded { WebAppDirTask appengineWebAppDirTask ->
            appengineWebAppDirTask.conventionMapping.map('webAppSourceDirectory') { getAppDir(project) }
        }
    }

    private void configureWorkingDirectory(Project project) {
        AppConfigTaskTemplate.metaClass.getWorkingDirectory = { -> this.explodedAppDirectory }
    }

    private void configureAppConfig(Project project) {
        project.tasks.withType(AppConfigTaskTemplate).whenTaskAdded { AppConfigTaskTemplate appengineAppConfigTaskTemplate ->
            appengineAppConfigTaskTemplate.conventionMapping.map('email') { this.appEnginePluginExtension.appCfg.email }
            appengineAppConfigTaskTemplate.conventionMapping.map('server') { this.appEnginePluginExtension.appCfg.server }
            appengineAppConfigTaskTemplate.conventionMapping.map('host') { this.appEnginePluginExtension.appCfg.host }
            appengineAppConfigTaskTemplate.conventionMapping.map('noCookies') { this.appEnginePluginExtension.appCfg.noCookies }
            appengineAppConfigTaskTemplate.conventionMapping.map('passIn') { this.appEnginePluginExtension.appCfg.passIn }
            appengineAppConfigTaskTemplate.conventionMapping.map('password') {
                // Password from gradle.properties takes precedence
                project.hasProperty(GRADLE_USER_PROP_PASSWORD) ? project.property(GRADLE_USER_PROP_PASSWORD) : this.appEnginePluginExtension.appCfg.password
            }
            appengineAppConfigTaskTemplate.conventionMapping.map('httpProxy') { this.appEnginePluginExtension.appCfg.httpProxy }
            appengineAppConfigTaskTemplate.conventionMapping.map('httpsProxy') { this.appEnginePluginExtension.appCfg.httpsProxy }
            appengineAppConfigTaskTemplate.conventionMapping.map('oauth2') { this.appEnginePluginExtension.appCfg.oauth2 }
            appengineAppConfigTaskTemplate.conventionMapping.map('extraOptions') { this.appEnginePluginExtension.appCfg.extraOptions }
        }
    }

    private void configureExplodeWarTask(Project project) {
        project.tasks.withType(ExplodeAppTask).whenTaskAdded { ExplodeAppTask appengineExplodeAppTask ->
            appengineExplodeAppTask.conventionMapping.map('archive') { project.hasProperty('ear') ? project.ear.archivePath : project.war.archivePath }
            appengineExplodeAppTask.conventionMapping.map(EXPLODED_WAR_DIR_CONVENTION_PARAM) { this.explodedAppDirectory }
        }

        ExplodeAppTask appengineExplodeAppTask = project.tasks.create(APPENGINE_EXPLODE_WAR, ExplodeAppTask)
        appengineExplodeAppTask.description = 'Explodes WAR archive into directory.'
        appengineExplodeAppTask.group = APPENGINE_GROUP

        project.afterEvaluate {
            if(project.hasProperty('ear')) {
                appengineExplodeAppTask.dependsOn project.ear
            } else if(project.hasProperty('war')) {
                appengineExplodeAppTask.dependsOn project.war
            }
        }

        // Always explode app when assembling
        project.tasks.getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn(appengineExplodeAppTask)
    }

    private void configureStageAppTask(Project project) {
        project.tasks.withType(StageTask).whenTaskAdded { StageTask appengineStageTask ->
            appengineStageTask.conventionMapping.map('stagedAppDirectory') { this.stagedAppDirectory }
            appengineStageTask.conventionMapping.map('explodedAppDirectory') { this.explodedAppDirectory }
        }

        StageTask appengineStageTask = project.tasks.create("appengineStage", StageTask)
        appengineStageTask.description = 'Stages an application for deployment.'
        appengineStageTask.group = APPENGINE_EXPERIMENTAL_GROUP

        ExplodeAppTask appengineExplodeAppTask = project.tasks.getByName(APPENGINE_EXPLODE_WAR)
        appengineStageTask.dependsOn appengineExplodeAppTask

        project.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
            if (taskGraph.hasTask(appengineStageTask)) {
                if (project.hasProperty('ear')) {
                    throw new BuildException("Staging does not support ear formatted applications", null);
                }
            }
        }
    }

    private void configureRun(Project project) {
        project.tasks.withType(RunTask).whenTaskAdded { RunTask appengineRunTask ->
            appengineRunTask.conventionMapping.map('httpAddress') { this.appEnginePluginExtension.httpAddress }
            appengineRunTask.conventionMapping.map('httpPort') { this.appEnginePluginExtension.httpPort }
            appengineRunTask.conventionMapping.map('daemon') { this.appEnginePluginExtension.daemon }
            appengineRunTask.conventionMapping.map('disableUpdateCheck') { this.appEnginePluginExtension.disableUpdateCheck }
            appengineRunTask.conventionMapping.map('disableDatagram') { this.appEnginePluginExtension.disableDatagram }
            appengineRunTask.conventionMapping.map('jvmFlags') { this.appEnginePluginExtension.jvmFlags }
            appengineRunTask.conventionMapping.map(EXPLODED_WAR_DIR_CONVENTION_PARAM) { this.appEnginePluginExtension.warDir ?: this.explodedAppDirectory }
        }

        RunTask appengineRunTask = project.tasks.create(APPENGINE_RUN, RunTask)
        appengineRunTask.description = 'Starts up a local App Engine development server.'
        appengineRunTask.group = APPENGINE_GROUP

        ExplodeAppTask appengineExplodeAppTask = project.tasks.getByName(APPENGINE_EXPLODE_WAR)
        appengineRunTask.dependsOn appengineExplodeAppTask

        // If WAR directory gets set we assume we have a fully functional web application, WAR creation/explosion is skipped
        appengineExplodeAppTask.onlyIf { !this.appEnginePluginExtension.warDir || !project.gradle.taskGraph.hasTask(appengineRunTask) }
    }

    private void configureStop(Project project) {
        project.tasks.withType(StopTask).whenTaskAdded { StopTask appengineStopTask ->
            appengineStopTask.conventionMapping.map('httpAddress') { this.appEnginePluginExtension.httpAddress }
            appengineStopTask.conventionMapping.map('httpPort') { this.appEnginePluginExtension.httpPort }
        }

        StopTask appengineStopTask = project.tasks.create(APPENGINE_STOP, StopTask)
        appengineStopTask.description = 'Stops local App Engine development server.'
        appengineStopTask.group = APPENGINE_GROUP
    }

    private void configureEnhance(Project project) {
        project.tasks.withType(EnhanceTask).whenTaskAdded { EnhanceTask appengineEnhanceTask ->
            appengineEnhanceTask.conventionMapping.map('classesDirectory') { project.tasks.compileJava.destinationDir }
            appengineEnhanceTask.conventionMapping.map('enhancerVersion') { this.appEnginePluginExtension.enhancer.version ?: this.appEnginePluginExtension.enhancerVersion }
            appengineEnhanceTask.conventionMapping.map('enhancerApi') { this.appEnginePluginExtension.enhancer.api ?: this.appEnginePluginExtension.enhancerApi }
        }

        EnhanceTask appengineEnhanceTask = project.tasks.create(APPENGINE_ENHANCE, EnhanceTask)
        appengineEnhanceTask.description = 'Enhances DataNucleus classes.'
        appengineEnhanceTask.group = APPENGINE_GROUP

        project.tasks.getByName(APPENGINE_ENHANCE).dependsOn project.classes

        project.afterEvaluate {
            if(this.appEnginePluginExtension.enhancer.enhanceOnBuild) {
                project.tasks.getByName(WarPlugin.WAR_TASK_NAME).dependsOn(appengineEnhanceTask)
            }
        }
    }

    private void configureUpdate(Project project) {
        project.tasks.withType(UpdateTask).whenTaskAdded { UpdateTask appengineUpdateTask ->
            appengineUpdateTask.conventionMapping.map(EXPLODED_WAR_DIR_CONVENTION_PARAM) { this.explodedAppDirectory }
            appengineUpdateTask.conventionMapping.map('useJava7') { this.appEnginePluginExtension.appCfg.update.useJava7 }
        }

        UpdateTask appengineUpdateTask = project.tasks.create(APPENGINE_UPDATE, UpdateTask)
        appengineUpdateTask.description = 'Updates your application on App Engine.'
        appengineUpdateTask.group = APPENGINE_GROUP
        appengineUpdateTask.dependsOn project.appengineExplodeApp
    }

    private void configureRollback(Project project) {
        RollbackTask appengineRollbackTask = project.tasks.create(APPENGINE_ROLLBACK, RollbackTask)
        appengineRollbackTask.description = 'Undoes a partially completed update for the given application.'
        appengineRollbackTask.group = APPENGINE_GROUP
    }

    private void configureUpdateIndexes(Project project) {
        UpdateIndexesTask appengineUpdateIndexesTask = project.tasks.create(APPENGINE_UPDATE_INDEXES, UpdateIndexesTask)
        appengineUpdateIndexesTask.description = 'Updates indexes on App Engine.'
        appengineUpdateIndexesTask.group = APPENGINE_GROUP
    }

    private void configureVacuumIndexes(Project project) {
        VacuumIndexesTask appengineVacuumIndexesTask = project.tasks.create(APPENGINE_VACUUM_INDEXES, VacuumIndexesTask)
        appengineVacuumIndexesTask.description = 'Deletes unused indexes on App Engine.'
        appengineVacuumIndexesTask.group = APPENGINE_GROUP
    }

    private void configureUpdateTaskQueues(Project project) {
        UpdateQueuesTask appengineUpdateQueuesTask = project.tasks.create(APPENGINE_UPDATE_TASK_QUEUES, UpdateQueuesTask)
        appengineUpdateQueuesTask.description = 'Updates task queues on App Engine.'
        appengineUpdateQueuesTask.group = APPENGINE_GROUP
    }

    private void configureUpdateDispatch(Project project) {
        UpdateDispatchTask appengineUpdateDispatchTask = project.tasks.create(APPENGINE_UPDATE_DISPATCH, UpdateDispatchTask)
        appengineUpdateDispatchTask.description = 'Updates dispatch file on App Engine.'
        appengineUpdateDispatchTask.group = APPENGINE_GROUP
    }

    private void configureUpdateDoS(Project project) {
        UpdateDoSTask appengineUpdateDoSTask = project.tasks.create(APPENGINE_UPDATE_DOS, UpdateDoSTask)
        appengineUpdateDoSTask.description = 'Updates DoS protection configuration on App Engine.'
        appengineUpdateDoSTask.group = APPENGINE_GROUP
    }

    private void configureUpdateCron(Project project) {
        UpdateCronTask appengineUpdateCronTask = project.tasks.create(APPENGINE_UPDATE_CRON, UpdateCronTask)
        appengineUpdateCronTask.description = 'Updates scheduled tasks definition (known as cron jobs) on App Engine.'
        appengineUpdateCronTask.group = APPENGINE_GROUP
    }

    private void configureCronInfo(Project project) {
        CronInfoTask appengineCronInfoTask = project.tasks.create(APPENGINE_CRON_INFO, CronInfoTask)
        appengineCronInfoTask.description = 'Get cron information from App Engine.'
        appengineCronInfoTask.group = APPENGINE_GROUP
    }

    private void configureDownloadLogs(Project project) {
        DownloadLogsTask appengineDownloadLogsTask = project.tasks.create(APPENGINE_LOGS, DownloadLogsTask)
        appengineDownloadLogsTask.description = 'Download logs from App Engine.'
        appengineDownloadLogsTask.group = APPENGINE_GROUP
        appengineDownloadLogsTask.conventionMapping.map('numDays') { this.appEnginePluginExtension.appCfg.logs.numDays }
        appengineDownloadLogsTask.conventionMapping.map('severity') { this.appEnginePluginExtension.appCfg.logs.severity }
        appengineDownloadLogsTask.conventionMapping.map('append') { this.appEnginePluginExtension.appCfg.logs.append }
        appengineDownloadLogsTask.conventionMapping.map('includeAll') { this.appEnginePluginExtension.appCfg.logs.includeAll }
        appengineDownloadLogsTask.conventionMapping.map('outputFile') { this.appEnginePluginExtension.appCfg.logs.outputFile }
    }

    private void configureVersion(Project project) {
        VersionTask appengineVersionTask = project.tasks.create(APPENGINE_VERSION, VersionTask)
        appengineVersionTask.description = 'Prints detailed version information about the SDK, Java and the operating system.'
        appengineVersionTask.group = APPENGINE_GROUP
    }

    private void configureDownloadApplication(Project project) {
        DownloadAppTask appengineDownloadAppTask = project.tasks.create(APPENGINE_DOWNLOAD_APP, DownloadAppTask)
        appengineDownloadAppTask.description = 'Retrieves the most current version of your application.'
        appengineDownloadAppTask.group = APPENGINE_GROUP
        appengineDownloadAppTask.conventionMapping.map('appId') { this.appEnginePluginExtension.appCfg.app.id }
        appengineDownloadAppTask.conventionMapping.map('appVersion') { this.appEnginePluginExtension.appCfg.app.version }
        appengineDownloadAppTask.conventionMapping.map('outputDirectory') { this.appEnginePluginExtension.appCfg.app.outputDirectory ?: this.downloadedAppDirectory }
    }

    /**
     * Configures Google App Engine SDK. If convention property was set to download specific SDK version use that one
     * for all tasks of the plugin.
     *
     * @param project Project
     * @param appEnginePluginExtension APPENGINE plugin convention
     */
    private void configureSdk(Project project) {
        project.tasks.all { Task task ->
            if(task.name.startsWith('appengine') && task.name != APPENGINE_DOWNLOAD_SDK) {
                task.dependsOn {
                    if(this.appEnginePluginExtension.downloadSdk) {
                        return task.project.tasks.getByName(APPENGINE_DOWNLOAD_SDK)
                    }
                }
            }
        }
    }

    private void configureSingleBackendTask(Project project) {
        project.tasks.withType(AbstractSingleBackendTask).whenTaskAdded { AbstractSingleBackendTask appengineSingleBackendTask ->
            appengineSingleBackendTask.conventionMapping.map('backend') { project.property(BACKEND_PROJECT_PROPERTY) }
        }
    }

    private void configureUpdateBackends(Project project) {
        UpdateBackendTask appengineUpdateBackendsTask = project.tasks.create(APPENGINE_UPDATE_BACKEND, UpdateBackendTask)
        appengineUpdateBackendsTask.description = 'Updates backend on App Engine.'
        appengineUpdateBackendsTask.group = APPENGINE_GROUP
        appengineUpdateBackendsTask.conventionMapping.map('backend') { project.property(BACKEND_PROJECT_PROPERTY) }
        appengineUpdateBackendsTask.conventionMapping.map(EXPLODED_WAR_DIR_CONVENTION_PARAM) { this.explodedAppDirectory }
        appengineUpdateBackendsTask.dependsOn project.appengineExplodeApp
    }

    private void configureUpdateAllBackends(Project project) {
        UpdateAllBackendsTask appengineUpdateAllBackendsTask = project.tasks.create(APPENGINE_UPDATE_ALL_BACKENDS, UpdateAllBackendsTask)
        appengineUpdateAllBackendsTask.description = 'Updates all backends on App Engine.'
        appengineUpdateAllBackendsTask.group = APPENGINE_GROUP
        appengineUpdateAllBackendsTask.conventionMapping.map(EXPLODED_WAR_DIR_CONVENTION_PARAM) { this.explodedAppDirectory }
        appengineUpdateAllBackendsTask.dependsOn project.appengineExplodeApp
    }

    private void configureRollbackBackends(Project project) {
        RollbackBackendTask appengineRollbackBackendsTask = project.tasks.create(APPENGINE_ROLLBACK_BACKEND, RollbackBackendTask)
        appengineRollbackBackendsTask.description = 'Rolls back backend on App Engine.'
        appengineRollbackBackendsTask.group = APPENGINE_GROUP
    }

    private void configureListBackends(Project project) {
        ListBackendsTask appengineListBackendsTask = project.tasks.create(APPENGINE_LIST_BACKENDS, ListBackendsTask)
        appengineListBackendsTask.description = 'Lists backends on App Engine.'
        appengineListBackendsTask.group = APPENGINE_GROUP
    }

    private void configureStartBackend(Project project) {
        StartBackendTask appengineStartBackendTask = project.tasks.create(APPENGINE_START_BACKEND, StartBackendTask)
        appengineStartBackendTask.description = 'Starts backend on App Engine.'
        appengineStartBackendTask.group = APPENGINE_GROUP
    }

    private void configureStopBackend(Project project) {
        StopBackendTask appengineStopBackendTask = project.tasks.create(APPENGINE_STOP_BACKEND, StopBackendTask)
        appengineStopBackendTask.description = 'Stops backend on App Engine.'
        appengineStopBackendTask.group = APPENGINE_GROUP
    }

    private void configureDeleteBackend(Project project) {
        DeleteBackendTask appengineDeleteBackendTask = project.tasks.create(APPENGINE_DELETE_BACKEND, DeleteBackendTask)
        appengineDeleteBackendTask.description = 'Deletes backend on App Engine.'
        appengineDeleteBackendTask.group = APPENGINE_GROUP
        appengineDeleteBackendTask.conventionMapping.map('backend') { project.property(BACKEND_PROJECT_PROPERTY) }
    }

    private void configureConfigureBackends(Project project) {
        ConfigureBackendsTask appengineConfigureBackendsTask = project.tasks.create(APPENGINE_CONFIGURE_BACKENDS, ConfigureBackendsTask)
        appengineConfigureBackendsTask.description = 'Configures backends on App Engine.'
        appengineConfigureBackendsTask.group = APPENGINE_GROUP
        appengineConfigureBackendsTask.conventionMapping.map('setting') { project.property(SETTING_PROJECT_PROPERTY) }
    }

    private void configureUpdateAll(Project project) {
        Task appengineUpdateAllTask = project.tasks.create(APPENGINE_UPDATE_ALL)
        appengineUpdateAllTask.description = 'Updates your application and all backends on App Engine.'
        appengineUpdateAllTask.group = APPENGINE_GROUP
        appengineUpdateAllTask.dependsOn project.appengineUpdate, project.appengineUpdateAllBackends
    }

    public void configureEndpoints(Project project) {
        project.tasks.withType(EndpointsTask).whenTaskAdded { EndpointsTask endpointsTask ->
            endpointsTask.conventionMapping.map('classesDirectory') { project.tasks.compileJava.destinationDir }
            endpointsTask.conventionMapping.map('webappDirectory') { getAppDir(project) }
            endpointsTask.conventionMapping.map('serviceClasses') { this.appEnginePluginExtension.endpoints.serviceClasses }

            if (endpointsTask instanceof GetDiscoveryDocsTask) {
                endpointsTask.conventionMapping.map(ENDPOINTS_DISCOVERY_DOC_CONVENTION_PARAM) { this.discoveryDocDirectory }
                endpointsTask.conventionMapping.map(ENDPOINTS_DISCOVERY_DOC_FORMAT_PARAM) { this.appEnginePluginExtension.endpoints.discoveryDocFormat }
            } else if (endpointsTask instanceof GetClientLibsTask || endpointsTask instanceof ClientLibProcessingTask) {
                endpointsTask.conventionMapping.map(ENDPOINTS_CLIENT_LIB_CONVENTION_PARAM) { this.endpointsClientLibDirectory }
            }
            if (endpointsTask instanceof ExportClientLibsTask) {
                endpointsTask.conventionMapping.map(ENDPOINTS_CLIENT_LIB_COPY_JAR_CONVENTION_PARAM) { this.appEnginePluginExtension.endpoints.clientLibJarOut }
                endpointsTask.conventionMapping.map(ENDPOINTS_CLIENT_LIB_COPY_SRC_JAR_CONVENTION_PARAM) { this.appEnginePluginExtension.endpoints.clientLibSrcJarOut }
            }
            if (endpointsTask instanceof ExpandClientLibsTask) {
                endpointsTask.conventionMapping.map("clientLibGenSrcDir") { this.endpointsExpandedSrcDirectory }
            }
        }

        if(project.hasProperty('war')) {
            // NOTE: Endpoints REQUIRES the war plugin
            // Adds the discovery doc generated path to the war archiving
            project.war.webInf { from this.discoveryDocDirectory.canonicalPath}
            // Make sure endpoints run before war tasks
            project.tasks.getByName(WarPlugin.WAR_TASK_NAME).mustRunAfter(project.tasks.withType(EndpointsTask))
        }

        GetDiscoveryDocsTask endpointsGetDiscoveryDocs = project.tasks.create(APPENGINE_ENDPOINTS_GET_DISCOVERY_DOCS, GetDiscoveryDocsTask)
        endpointsGetDiscoveryDocs.description = 'Generate Endpoints discovery docs for classes defined in web.xml'
        endpointsGetDiscoveryDocs.group = APPENGINE_GROUP
        endpointsGetDiscoveryDocs.dependsOn(project.tasks.getByName(JavaPlugin.CLASSES_TASK_NAME))

        GetClientLibsTask endpointsGetClientLibs = project.tasks.create(APPENGINE_ENDPOINTS_GET_CLIENT_LIBS, GetClientLibsTask)
        endpointsGetClientLibs.description = 'Generate Endpoints java client libraries for classes defined in web.xml'
        endpointsGetClientLibs.group = APPENGINE_GROUP
        endpointsGetClientLibs.dependsOn(project.tasks.getByName(JavaPlugin.CLASSES_TASK_NAME))

        InstallClientLibsTask endpointsInstallClientLibs = project.tasks.create(APPENGINE_ENDPOINTS_INSTALL_CLIENT_LIBS, InstallClientLibsTask)
        endpointsInstallClientLibs.description = 'Install generated client libs into the local Maven repository'
        endpointsInstallClientLibs.group = APPENGINE_GROUP
        endpointsInstallClientLibs.dependsOn(endpointsGetClientLibs)

        ExportClientLibsTask endpointsExportClientLibs = project.tasks.create(APPENGINE_ENDPOINTS_EXPORT_CLIENT_LIBS, ExportClientLibsTask)
        endpointsExportClientLibs.description = 'Export the generated client libraries jars to a user-defined destination'
        endpointsExportClientLibs.group = APPENGINE_GROUP
        endpointsExportClientLibs.dependsOn(endpointsGetClientLibs)

        ExpandClientLibsTask endpointsExpandClientLibs = project.tasks.create(APPENGINE_ENDPOINTS_EXPAND_CLIENT_LIBS, ExpandClientLibsTask)
        endpointsExpandClientLibs.description = 'Expand the generated client libraries sources in to build/generated-source'
        endpointsExpandClientLibs.group = APPENGINE_GROUP
        endpointsExpandClientLibs.dependsOn(endpointsGetClientLibs)

        project.afterEvaluate {
            if(this.appEnginePluginExtension.endpoints.getDiscoveryDocsOnBuild) {
                project.tasks.getByName(WarPlugin.WAR_TASK_NAME).dependsOn(endpointsGetDiscoveryDocs)
            }
            if(this.appEnginePluginExtension.endpoints.getClientLibsOnBuild) {
                project.tasks.getByName(WarPlugin.WAR_TASK_NAME).dependsOn(endpointsGetClientLibs)
            }
            if(this.appEnginePluginExtension.endpoints.installClientLibsOnBuild) {
                project.tasks.getByName(WarPlugin.WAR_TASK_NAME).dependsOn(endpointsInstallClientLibs)
            }
            if(this.appEnginePluginExtension.endpoints.exportClientLibsOnBuild) {
                project.tasks.getByName(WarPlugin.WAR_TASK_NAME).dependsOn(endpointsExportClientLibs)
            }
        }
        configureEndpointsConfigurations(project)
    }

    private void configureEndpointsConfigurations(Project project) {
        final String ENDPOINTS_SOURCE_SET = "endpointsSrc"
        final String ENDPOINTS_CONFIG = "endpoints"
        final String ANDROID_CONFIG = "android-endpoints"

        // Pull in the expanded source for endpoints as a source set of this project
        SourceSet endpointsSrc = project.sourceSets.create(ENDPOINTS_SOURCE_SET)
        endpointsSrc.getJava().setSrcDirs([this.endpointsExpandedSrcDirectory])
        project.tasks.getByName(endpointsSrc.getCompileJavaTaskName()).dependsOn(
                project.tasks.getByName(APPENGINE_ENDPOINTS_EXPAND_CLIENT_LIBS))

        // configure the configurations so other modules can depend on endpoints
        project.configurations.create(ENDPOINTS_CONFIG)
        project.configurations.create(ANDROID_CONFIG)

        project.afterEvaluate {
            final String GOOGLE_API_LIB_VERSION = this.appEnginePluginExtension.endpoints.googleClientVersion
            final String GOOGLE_API_LIB = "com.google.api-client:google-api-client:${GOOGLE_API_LIB_VERSION}"
            final String GOOGLE_ANDROID_API_LIB = "com.google.api-client:google-api-client-android:${GOOGLE_API_LIB_VERSION}"
            final String EXCLUDE_HTTP_GROUP = "org.apache.httpcomponents"
            final String EXCLUDE_HTTP_MODULE = "httpclient"
            project.dependencies.add(endpointsSrc.getCompileConfigurationName(), GOOGLE_API_LIB)
            project.dependencies.add(ENDPOINTS_CONFIG, GOOGLE_API_LIB)
            project.dependencies.add(ANDROID_CONFIG, GOOGLE_ANDROID_API_LIB)
            project.configurations.findByName(ANDROID_CONFIG).exclude(group: EXCLUDE_HTTP_GROUP, module: EXCLUDE_HTTP_MODULE)
        }

        // create the archive tasks
        Jar endpointsJarTask = project.tasks.create("_appengineEndpointsArtifact", Jar)
        endpointsJarTask.description = "Internal task, do not use"
        endpointsJarTask.classifier = ENDPOINTS_CONFIG
        endpointsJarTask.from(endpointsSrc.output)

        Jar endpointsAndroidJarTask = project.tasks.create("_appengineEndpointsAndroidArtifact", Jar)
        endpointsAndroidJarTask.description = "Internal task, do not use"
        endpointsAndroidJarTask.classifier = ANDROID_CONFIG
        endpointsAndroidJarTask.from(endpointsSrc.output)

        // add the archive task to the configurations so the generated jars are usuable dependencies of the configuration
        project.artifacts.add(ENDPOINTS_CONFIG, endpointsJarTask)
        project.artifacts.add(ANDROID_CONFIG, endpointsAndroidJarTask)
    }

    private void configureFunctionalTest(Project project) {
        SourceSet functionalSourceSet = addFunctionalTestConfigurationsAndSourceSet(project)

        Test appengineFunctionalTest = project.tasks.create(APPENGINE_FUNCTIONAL_TEST, Test)
        appengineFunctionalTest.description = 'Runs functional tests'
        appengineFunctionalTest.group = APPENGINE_GROUP
        appengineFunctionalTest.testClassesDir = functionalSourceSet.output.classesDir
        appengineFunctionalTest.classpath = functionalSourceSet.runtimeClasspath
        RunTask runTask = project.tasks.getByName(APPENGINE_RUN)
        runTask.mustRunAfter(functionalSourceSet.classesTaskName, functionalSourceSet.processResourcesTaskName, functionalSourceSet.compileJavaTaskName)
        appengineFunctionalTest.dependsOn(runTask)
        appengineFunctionalTest.finalizedBy(project.tasks.getByName(APPENGINE_STOP))

        File serverTrigger = project.file("${project.buildDir}/server.trigger")
        project.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
            if(taskGraph.hasTask(appengineFunctionalTest)) {
                if(APPENGINE_RUN in project.gradle.startParameter.taskNames) {
                    project.logger.warn("WARN: Explicitly called task ${APPENGINE_RUN} will not behave normally when run with ${APPENGINE_FUNCTIONAL_TEST}")
                }
                this.appEnginePluginExtension.daemon = true
                runTask.inputs.files(appengineFunctionalTest.inputs.files, serverTrigger)
                runTask.outputs.files(appengineFunctionalTest.outputs.files)
            }
        }

        project.gradle.taskGraph.afterTask { Task task, TaskState state ->
            if(task.project != project) {
                return
            }
            if(task.name == APPENGINE_FUNCTIONAL_TEST && state.failure) {
                serverTrigger.withWriter { w ->
                    w << new Date()
                }
            }
            if(task.name == APPENGINE_RUN && state.skipped) {
                project.tasks.getByName(APPENGINE_STOP).enabled = false
            }
        }

        project.tasks.getByName(JavaBasePlugin.CHECK_TASK_NAME).dependsOn appengineFunctionalTest
    }

    private SourceSet addFunctionalTestConfigurationsAndSourceSet(Project project) {
        ConfigurationContainer configurations = project.configurations
        Configuration functionalTestCompileConfiguration = configurations.create(FUNCTIONAL_TEST_COMPILE_CONFIGURATION)
        Configuration testCompileConfiguration = configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME)
        functionalTestCompileConfiguration.extendsFrom(testCompileConfiguration)

        Configuration functionalTestRuntimeConfiguration = configurations.create(FUNCTIONAL_TEST_RUNTIME_CONFIGURATION)
        Configuration testRuntimeConfiguration = configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)
        functionalTestRuntimeConfiguration.extendsFrom(functionalTestCompileConfiguration, testRuntimeConfiguration)

        SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets
        SourceSet mainSourceSet = sourceSets.getByName(MAIN_SOURCE_SET_NAME)
        SourceSet functionalSourceSet = sourceSets.create(FUNCTIONAL_TEST_SOURCE_SET)
        functionalSourceSet.compileClasspath = mainSourceSet.output + functionalTestCompileConfiguration
        functionalSourceSet.runtimeClasspath = mainSourceSet.output + functionalSourceSet.output + functionalTestRuntimeConfiguration

        addEclipseConfigurationForFunctionalTestRuntimeConfiguration(project, functionalTestRuntimeConfiguration)
        addIdeaConfigurationForFunctionalTestSourceSet(project, functionalTestCompileConfiguration, functionalTestRuntimeConfiguration, functionalSourceSet)

        functionalSourceSet
    }

    private void addIdeaConfigurationForFunctionalTestSourceSet(Project project, Configuration compile, Configuration runtime, SourceSet sourceSet) {
        project.plugins.withType(IdeaPlugin) { IdeaPlugin plugin ->
            plugin.model.module {
                testSourceDirs += sourceSet.allSource.srcDirs
                scopes.TEST.plus += [ compile ]
                scopes.TEST.plus += [ runtime ]
            }
        }
    }

    private void addEclipseConfigurationForFunctionalTestRuntimeConfiguration(Project project, Configuration functionalTestRuntimeConfiguration) {
        //TODO : once we up the minimum supported version, remove this conditional
        if(VersionComparator.compare(project.gradle.gradleVersion, "2.3") < 0) {
            project.plugins.withType(EclipsePlugin) { EclipsePlugin plugin ->
                plugin.model.classpath.plusConfigurations += [functionalTestRuntimeConfiguration]
            }
        }
        else {
            project.afterEvaluate {
                if (project.plugins.hasPlugin(EclipsePlugin)) {
                    EclipseModel model = project.extensions.getByType(EclipseModel)
                    model.classpath.plusConfigurations += [functionalTestRuntimeConfiguration]
                }
            }
        }
    }

    static File getAppDir(Project project) {
        if(project.hasProperty('ear')) {
            return new File(project.projectDir, project.convention.getPlugin(EarPluginConvention).appDirName)
        } else if(project.hasProperty('war')) {
            return project.convention.getPlugin(WarPluginConvention).webAppDir
        }
    }
}

