// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.project.actions.LookForNestedToggleAction
import org.jetbrains.idea.maven.server.MavenWrapperDownloader
import org.jetbrains.idea.maven.server.MavenWrapperSupport.Companion.getWrapperDistributionUrl
import org.jetbrains.idea.maven.telemetry.tracer
import org.jetbrains.idea.maven.utils.*
import java.nio.file.Path

class MavenProjectAsyncBuilder {
  fun commitSync(project: Project, projectFile: VirtualFile, modelsProvider: IdeModifiableModelsProvider?): List<Module> {
    if (ApplicationManager.getApplication().isDispatchThread) {
      return runWithModalProgressBlocking(project, MavenProjectBundle.message("maven.reading")) {
        commit(project, projectFile, modelsProvider, true)
      }
    }
    else {
      return runBlockingMaybeCancellable {
        commit(project, projectFile, modelsProvider, true)
      }
    }
  }

  suspend fun commit(project: Project, projectFile: VirtualFile) {
    commit(project, projectFile, null, true)
  }

  suspend fun commit(
    project: Project,
    projectFile: VirtualFile,
    modelsProvider: IdeModifiableModelsProvider?,
    syncProject: Boolean,
  ): List<Module> = project.trackActivity(MavenActivityKey) {
    if (ApplicationManager.getApplication().isDispatchThread) {
      writeIntentReadAction {
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }

    val importProjectFile = if (!projectFile.isDirectory) projectFile else null
    val rootDirectory = if (projectFile.isDirectory) projectFile else projectFile.parent
    val rootDirectoryPath = rootDirectory.toNioPath()

    val isVeryNewProject = true == project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT)
    val createDummyModule = isVeryNewProject && Registry.`is`("maven.create.dummy.module.on.first.import")

    val directProjectsSettings = MavenWorkspaceSettingsComponent.getInstance(project).settings
    val importingSettings = directProjectsSettings.importingSettings.clone()
    val generalSettings = directProjectsSettings.generalSettings.clone()

    if (isVeryNewProject) {
      blockingContext { ExternalProjectsManagerImpl.setupCreatedProject(project) }
    }

    if (createDummyModule) {
      val previewModule = createPreviewModule(project, rootDirectory)
      // do not update all modules because it can take a lot of time (freeze at project opening)
      val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
      cs.launch {
        project.trackActivity(MavenActivityKey) {
          doCommit(project,
                   importProjectFile,
                   rootDirectoryPath,
                   modelsProvider,
                   previewModule,
                   importingSettings,
                   generalSettings,
                   syncProject)
        }
      }
      return@trackActivity if (null == previewModule) emptyList() else listOf(previewModule)
    }

    return@trackActivity doCommit(project,
                                  importProjectFile,
                                  rootDirectoryPath,
                                  modelsProvider,
                                  null,
                                  importingSettings,
                                  generalSettings,
                                  syncProject)
  }

  private suspend fun doCommit(
    project: Project,
    importProjectFile: VirtualFile?,
    rootDirectory: Path,
    modelsProvider: IdeModifiableModelsProvider?,
    previewModule: Module?,
    importingSettings: MavenImportingSettings,
    generalSettings: MavenGeneralSettings,
    syncProject: Boolean,
  ): List<Module> {
    MavenAsyncUtil.setupProjectSdk(project)
    val projectsNavigator = MavenProjectsNavigator.getInstance(project)
    if (projectsNavigator != null) projectsNavigator.groupModules = true

    val files: List<VirtualFile?> = withBackgroundProgress(project, MavenProjectBundle.message("maven.reading"), true) {
      coroutineToIndicator {
        val indicator = ProgressManager.getGlobalProgressIndicator()
        if (importProjectFile != null) {
          return@coroutineToIndicator listOf(importProjectFile)
        }
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(
          FileUtil.toSystemIndependentName(rootDirectory.toString()))
        if (virtualFile == null) {
          return@coroutineToIndicator emptyList()
        }
        return@coroutineToIndicator FileFinder.findPomFiles(virtualFile.children, LookForNestedToggleAction.isSelected(), indicator)
      }
    }

    val tree = MavenProjectsTree(project)
    tree.addManagedFilesWithProfiles(files, MavenExplicitProfiles.NONE)

    generalSettings.updateFromMavenConfig(files)
    updateMavenSettingsFromEnvironment(project, generalSettings, importingSettings)

    val manager = MavenProjectsManager.getInstance(project)

    if (project.isTrusted()) {
      withBackgroundProgress(project, MavenProjectBundle.message("maven.installing.wrapper"), false) {
        withContext(tracer.span("installingMavenWrapperBeforeSync") + Dispatchers.IO) {
          MavenWrapperDownloader.checkOrInstallForSync(project, rootDirectory.toString(), false);
        }
      }
    }


    withBackgroundProgress(project, MavenProjectBundle.message("maven.reading"), false) {
      reportRawProgress { reporter ->
        tree.updateAll(false, generalSettings, reporter)
      }
    }
    val projects = tree.rootProjects
    if (projects.isEmpty()) {
      LOG.warn(String.format("Cannot import project for %s", project.toString()))
      return emptyList()
    }

    val selectedProfiles = getProfilesFromSystemProperties()

    manager.setIgnoredState(projects, false)

    return manager.addManagedFilesWithProfiles(MavenUtil.collectFiles(projects), selectedProfiles, modelsProvider, previewModule, syncProject)
  }

  private fun getProfilesFromSystemProperties(): MavenExplicitProfiles {
    val selectedProfiles = MavenExplicitProfiles.NONE.clone()
    val enabledProfilesList = System.getProperty("idea.maven.import.enabled.profiles")
    val disabledProfilesList = System.getProperty("idea.maven.import.disabled.profiles")
    if (enabledProfilesList != null || disabledProfilesList != null) {
      appendProfilesFromString(selectedProfiles.enabledProfiles, enabledProfilesList)
      appendProfilesFromString(selectedProfiles.disabledProfiles, disabledProfilesList)
    }
    return selectedProfiles
  }

  private fun updateMavenSettingsFromEnvironment(
    project: Project,
    generalSettings: MavenGeneralSettings,
    importingSettings: MavenImportingSettings,
  ) {
    val settings = MavenWorkspaceSettingsComponent.getInstance(project).settings
    settings.generalSettings = generalSettings
    settings.importingSettings = importingSettings
    val settingsFile = System.getProperty("idea.maven.import.settings.file")
    if (!settingsFile.isNullOrBlank()) {
      settings.generalSettings.setUserSettingsFile(settingsFile.trim { it <= ' ' })
    }
    val distributionUrl = getWrapperDistributionUrl(project.guessProjectDir())
    if (distributionUrl != null) {
      settings.generalSettings.mavenHomeType = MavenWrapper
    }
  }

  private suspend fun createPreviewModule(project: Project, contentRoot: VirtualFile): Module? {
    if (ModuleManager.getInstance(project).modules.isEmpty()) {
      return withContext(Dispatchers.EDT) { MavenImportUtil.createPreviewModule(project, contentRoot) }
    }
    return null
  }

  companion object {
    private val LOG = Logger.getInstance(MavenProjectAsyncBuilder::class.java)

    private fun appendProfilesFromString(selectedProfiles: MutableCollection<String>, profilesList: String?) {
      if (profilesList == null) return
      for (profile in StringUtil.split(profilesList, ",")) {
        val trimmedProfileName = profile.trim { it <= ' ' }
        if (!trimmedProfileName.isEmpty()) {
          selectedProfiles.add(trimmedProfileName)
        }
      }
    }
  }
}
