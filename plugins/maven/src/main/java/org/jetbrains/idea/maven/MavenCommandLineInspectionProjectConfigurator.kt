// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.ide.CommandLineInspectionProjectAsyncConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.ide.environment.EnvironmentService
import com.intellij.ide.impl.ProjectOpenKeyProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.LanguageLevelUtil.getNextLanguageLevel
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkLookup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.pom.java.LanguageLevel.HIGHEST
import com.intellij.util.lang.JavaVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider
import java.util.concurrent.CompletableFuture
import kotlin.io.path.pathString

private const val MAVEN_CREATE_DUMMY_MODULE_ON_FIRST_IMPORT_REGISTRY_KEY = "maven.create.dummy.module.on.first.import"
private val LOG = Logger.getInstance(MavenCommandLineInspectionProjectConfigurator::class.java)
private const val DISABLE_EXTERNAL_SYSTEM_AUTO_IMPORT = "external.system.auto.import.disabled"
private const val MAVEN_COMMAND_LINE_CONFIGURATOR_EXIT_ON_UNRESOLVED_PLUGINS = "maven.command.line.configurator.exit.on.unresolved.plugins"
private val MAVEN_OUTPUT_LOG = Logger.getInstance("MavenOutput")

class MavenCommandLineInspectionProjectConfigurator : CommandLineInspectionProjectAsyncConfigurator {
  override fun getName(): String = "maven"

  override fun getDescription(): String = MavenProjectBundle.message("maven.commandline.description")

  override fun configureEnvironment(context: ConfiguratorContext) = context.run {
    System.setProperty(MAVEN_CREATE_DUMMY_MODULE_ON_FIRST_IMPORT_REGISTRY_KEY, false.toString())
    Unit
  }

  override suspend fun configureProjectAsync(project: Project, context: ConfiguratorContext) {
    val basePath = context.projectPath.pathString
    val pomXmlFile = basePath + "/" + MavenConstants.POM_XML
    if (FileUtil.findFirstThatExist(pomXmlFile) == null) return

    val service = service<EnvironmentService>()
    val projectSelectionKey = service.getEnvironmentValue(ProjectOpenKeyProvider.Keys.PROJECT_OPEN_PROCESSOR, "Maven")

    if (projectSelectionKey != "Maven") { // something else was selected to open the project
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
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          FileDocumentManager.getInstance().saveAllDocuments()
          MavenUtil.setupProjectSdk(project)
        }
      }

      // GradleWarmupConfigurator sets "external.system.auto.import.disabled" to true, but we have to import the project nevertheless
      MavenOpenProjectProvider().forceLinkToExistingProjectAsync(basePath, project)
    }
    MavenLog.LOG.warn("linked finished for ${project.name}")
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)

    Disposer.dispose(disposable)

    for (mavenProject in mavenProjectsManager.projects) {
      val hasReadingProblems = mavenProject.hasReadingProblems()
      if (hasReadingProblems) {
        throw IllegalStateException("Maven project ${mavenProject} has import problems:" + mavenProject.problems)
      }

      val hasUnresolvedArtifacts = mavenProject.hasUnresolvedArtifacts()
      if (hasUnresolvedArtifacts) {
        val unresolvedArtifacts = mavenProject.dependencies.filterNot { it.isResolved } + mavenProject.externalAnnotationProcessors.filterNot { it.isResolved }
        throw IllegalStateException("Maven project ${mavenProject} has unresolved artifacts: $unresolvedArtifacts")
      }

      val hasUnresolvedPlugins = mavenProject.hasUnresolvedPlugins()
      if (hasUnresolvedPlugins) {
        val unresolvedPlugins = mavenProject.declaredPlugins.filterNot { plugin ->
          MavenArtifactUtil.hasArtifactFile(mavenProject.localRepository, plugin.mavenId)
        }
        val errorMessage = "maven project: ${mavenProject} has unresolved plugins: $unresolvedPlugins"
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

    val sdk = setupJdkWithSuitableVersion(projects, context.progressIndicator).get()

    if (sdk != null) {
      invokeAndWaitIfNeeded {
        runWriteAction {
          ProjectRootManager.getInstance(project).projectSdk = sdk
        }
      }
    }
  }

  fun setupJdkWithSuitableVersion(projects: List<MavenProject>, indicator: ProgressIndicator): CompletableFuture<Sdk?> {
    val maxLevel = projects.flatMap {
      val javaVersions = MavenImportUtil.getMavenJavaVersions(it)
      listOf(javaVersions.sourceLevel, javaVersions.testSourceLevel, javaVersions.targetLevel, javaVersions.testTargetLevel)
    }.filterNotNull().maxWithOrNull(Comparator.naturalOrder()) ?: HIGHEST

    return iterateVersions(maxLevel, indicator)
  }

  private fun iterateVersions(level: LanguageLevel?, progressIndicator: ProgressIndicator): CompletableFuture<Sdk?> {
    if (level == null) {
      return CompletableFuture.completedFuture(null)
    }
    val future = CompletableFuture<Sdk?>()
    SdkLookup
      .newLookupBuilder()
      .withProgressIndicator(progressIndicator)
      .withVersionFilter { JavaVersion.tryParse(it)?.feature == level.feature() }
      .withSdkType(JavaSdk.getInstance())
      .onSdkResolved { sdk ->
        future.complete(sdk)
      }.executeLookup()
    return future.thenCompose { sdk ->
      if (sdk != null) {
        CompletableFuture.completedFuture(sdk)
      }
      else {
        iterateVersions(getNextLanguageLevel(level), progressIndicator)
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
