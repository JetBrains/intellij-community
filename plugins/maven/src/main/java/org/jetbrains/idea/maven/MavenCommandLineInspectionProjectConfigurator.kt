// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.ide.environment.EnvironmentParametersService
import com.intellij.ide.impl.ProjectOpenKeyRegistry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager
import com.intellij.openapi.module.LanguageLevelUtil.getNextLanguageLevel
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkLookup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.java.LanguageLevel
import com.intellij.pom.java.LanguageLevel.HIGHEST
import com.intellij.util.ExceptionUtil
import com.intellij.util.lang.JavaVersion
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.resolved
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.pathString

private const val MAVEN_CREATE_DUMMY_MODULE_ON_FIRST_IMPORT_REGISTRY_KEY = "maven.create.dummy.module.on.first.import"
private val LOG = Logger.getInstance(MavenCommandLineInspectionProjectConfigurator::class.java)
private const val DISABLE_EXTERNAL_SYSTEM_AUTO_IMPORT = "external.system.auto.import.disabled"
private const val MAVEN_COMMAND_LINE_CONFIGURATOR_EXIT_ON_UNRESOLVED_PLUGINS = "maven.command.line.configurator.exit.on.unresolved.plugins"
private const val MAVEN_LINEAR_IMPORT = "maven.linear.import"
private val MAVEN_OUTPUT_LOG = Logger.getInstance("MavenOutput")

class MavenCommandLineInspectionProjectConfigurator : CommandLineInspectionProjectConfigurator {
  override fun getName(): String = "maven"

  override fun getDescription(): String = MavenProjectBundle.message("maven.commandline.description")

  override fun configureEnvironment(context: ConfiguratorContext) = context.run {
    Registry.get(DISABLE_EXTERNAL_SYSTEM_AUTO_IMPORT).setValue(true)
    Registry.get(MAVEN_CREATE_DUMMY_MODULE_ON_FIRST_IMPORT_REGISTRY_KEY).setValue(false)
  }

  override fun configureProject(project: Project, context: ConfiguratorContext) {
    val basePath = context.projectPath.pathString
    val pomXmlFile = basePath + "/" + MavenConstants.POM_XML
    if (FileUtil.findFirstThatExist(pomXmlFile) == null) return

    val service = service<EnvironmentParametersService>()
    val projectSelectionKey = runBlockingCancellable { service.getEnvironmentValueOrNull(ProjectOpenKeyRegistry.PROJECT_OPEN_PROCESSOR) }
    if (projectSelectionKey != null && projectSelectionKey != "Maven") {
      // something else was selected to open the project
      return
    }

    val mavenProjectAware = ExternalSystemUnlinkedProjectAware.getInstance(MavenUtil.SYSTEM_ID)!!
    val isMavenProjectLinked = mavenProjectAware.isLinkedProject(project, basePath)

    LOG.info("maven project: ${project.name} is linked: $isMavenProjectLinked")

    val disposable = Disposer.newDisposable()
    val progressListener = LogBuildProgressListener()
    val externalSystemRunConfigurationViewManager = project.service<ExternalSystemRunConfigurationViewManager>()
    val buildViewManager = project.service<BuildViewManager>()
    val syncViewManager = project.service<SyncViewManager>()

    externalSystemRunConfigurationViewManager.addListener(progressListener, disposable)
    buildViewManager.addListener(progressListener, disposable)
    syncViewManager.addListener(progressListener, disposable)

    if (!isMavenProjectLinked) {
      ApplicationManager.getApplication().invokeAndWait {
        mavenProjectAware.linkAndLoadProject(project, basePath)
      }
    }
    MavenLog.LOG.warn("linked finished for ${project.name}")
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val promise = mavenProjectsManager.waitForImportCompletion()
    while (true) {
      try {
        promise.blockingGet(10, TimeUnit.MILLISECONDS)
        break
      }
      catch (e: TimeoutException) {

      }
      catch (e: ExecutionException) {
        ExceptionUtil.rethrow(e)
      }
      ProgressManager.checkCanceled()
    }

    Disposer.dispose(disposable)

    for (mavenProject in mavenProjectsManager.projects) {
      val hasReadingProblems = mavenProject.hasReadingProblems()
      if (hasReadingProblems) {
        throw IllegalStateException("Maven project ${mavenProject.name} has import problems:" + mavenProject.problems)
      }

      val hasUnresolvedArtifacts = mavenProject.hasUnresolvedArtifacts()
      if (hasUnresolvedArtifacts) {
        val unresolvedArtifacts = mavenProject.dependencies.filterNot { it.resolved() } +
                                  mavenProject.externalAnnotationProcessors.filterNot { it.resolved() }
        throw IllegalStateException("Maven project ${mavenProject.name} has unresolved artifacts: $unresolvedArtifacts")
      }

      val hasUnresolvedPlugins = mavenProject.hasUnresolvedPlugins()
      if (hasUnresolvedPlugins) {
        val unresolvedPlugins = mavenProject.declaredPlugins.filterNot { plugin ->
          MavenArtifactUtil.hasArtifactFile(mavenProject.localRepository, plugin.mavenId)
        }
        val errorMessage = "maven project: ${mavenProject.name} has unresolved plugins: $unresolvedPlugins"
        if (System.getProperty(MAVEN_COMMAND_LINE_CONFIGURATOR_EXIT_ON_UNRESOLVED_PLUGINS, "false").toBoolean()) {
          throw IllegalStateException(errorMessage)
        }
        else {
          LOG.warn(errorMessage)
        }
      }
    }

    if (mavenProjectsManager.projects.isNotEmpty()) {
      setupDefaultJdk(mavenProjectsManager.projects, context, project)
    }
  }

  private fun setupDefaultJdk(projects: List<MavenProject>, context: ConfiguratorContext, project: Project) {

    if (ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).isNotEmpty()) return

    val maxLevel = projects.flatMap {
      val javaVersions = MavenImportUtil.getMavenJavaVersions(it)
      listOf(javaVersions.sourceLevel, javaVersions.testSourceLevel, javaVersions.targetLevel, javaVersions.testTargetLevel)
    }.filterNotNull().maxWithOrNull(Comparator.naturalOrder()) ?: HIGHEST

    setupJdkWithVersionAboveOrEqual(maxLevel, project, context)
  }

  private fun setupJdkWithVersionAboveOrEqual(maxLevel: LanguageLevel,
                                              project: Project,
                                              context: ConfiguratorContext) {
    var currentLevel: LanguageLevel? = maxLevel
    while (currentLevel != null) {
      val level = currentLevel
      val future = CompletableFuture<Sdk?>()
      SdkLookup
        .newLookupBuilder()
        .withProgressIndicator(context.progressIndicator)
        .withVersionFilter { JavaVersion.tryParse(it)?.feature == level.toJavaVersion().feature }
        .withSdkType(JavaSdk.getInstance())
        .onSdkResolved { sdk ->
          future.complete(sdk)
        }
        .executeLookup()

      val sdk = future.get()
      if (sdk != null) {
        invokeAndWaitIfNeeded {
          runWriteAction {
            ProjectRootManager.getInstance(project).projectSdk = sdk
          }
        }
        return
      }
      else {
        currentLevel = getNextLanguageLevel(currentLevel)
      }
    }
  }

  class LogBuildProgressListener : BuildProgressListener {
    override fun onEvent(buildId: Any, event: BuildEvent) {
      val outputBuildEvent = event as? OutputBuildEvent ?: return
      val prefix = if (outputBuildEvent.isStdOut) "" else "stderr: "
      MAVEN_OUTPUT_LOG.debug(prefix + outputBuildEvent.message)
    }
  }
}