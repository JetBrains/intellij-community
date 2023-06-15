// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.project.actions.LookForNestedToggleAction
import org.jetbrains.idea.maven.project.importing.FilesList
import org.jetbrains.idea.maven.project.importing.MavenImportingManager
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.MavenWrapperSupport.Companion.getWrapperDistributionUrl
import org.jetbrains.idea.maven.utils.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

internal class MavenProjectAsyncBuilder {
  var isUpdate = false

  private class Parameters {
    var myProjectToUpdate: Project? = null
    var myGeneralSettingsCache: MavenGeneralSettings? = null
    var myImportingSettingsCache: MavenImportingSettings? = null
    var myImportRootDirectory: Path? = null
    var myImportProjectFile: VirtualFile? = null
    var myFiles: List<VirtualFile?>? = null
    var myMavenProjectTree: MavenProjectsTree? = null
    var mySelectedProjects: List<MavenProject>? = null
  }

  private val parameters: Parameters = Parameters()

  fun setFileToImport(file: VirtualFile) {
    if (!file.isDirectory) parameters.myImportProjectFile = file
    parameters.myImportRootDirectory = if (file.isDirectory) file.toNioPath() else file.parent.toNioPath()
  }

  suspend fun commit(project: Project, model: ModifiableModuleModel?, modulesProvider: ModulesProvider?): List<Module?>? {
    return commit(project, model, modulesProvider, null)
  }

  private val projectToUpdate: Project?
    private get() {
      if (parameters.myProjectToUpdate == null) {
        parameters.myProjectToUpdate = currentProject
      }
      return parameters.myProjectToUpdate
    }
  private val directProjectsSettings: MavenWorkspaceSettings
    private get() {
      ApplicationManager.getApplication().assertReadAccessAllowed()
      var project = if (isUpdate) projectToUpdate else null
      if (project == null || project.isDisposed) project = ProjectManager.getInstance().defaultProject
      return MavenWorkspaceSettingsComponent.getInstance(project).settings
    }
  private val importingSettings: MavenImportingSettings?
    private get() {
      if (parameters.myImportingSettingsCache == null) {
        ApplicationManager.getApplication().runReadAction { parameters.myImportingSettingsCache = directProjectsSettings.getImportingSettings().clone() }
      }
      return parameters.myImportingSettingsCache
    }
  private val rootPath: Path?
    private get() {
      if (parameters.myImportRootDirectory == null && isUpdate) {
        val project = projectToUpdate
        parameters.myImportRootDirectory = if (project == null) null else Paths.get(Objects.requireNonNull(project.basePath))
      }
      return parameters.myImportRootDirectory
    }
  private val projectOrDefault: Project
    private get() {
      var project = projectToUpdate
      if (project == null || project.isDisposed) project = ProjectManager.getInstance().defaultProject
      return project
    }

  @Throws(MavenProcessCanceledException::class)
  private fun getProjectFiles(indicator: MavenProgressIndicator): List<VirtualFile?> {
    if (parameters.myImportProjectFile != null) {
      return listOf(parameters.myImportProjectFile)
    }
    val file = rootPath
    val virtualFile = if (file == null) null
    else LocalFileSystem.getInstance()
      .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString()))
    if (virtualFile == null) {
      throw MavenProcessCanceledException()
    }
    return FileFinder.findPomFiles(virtualFile.children, LookForNestedToggleAction.isSelected(), indicator)
  }

  private val generalSettings: MavenGeneralSettings?
    private get() {
      if (parameters.myGeneralSettingsCache == null) {
        ApplicationManager.getApplication().runReadAction {
          parameters.myGeneralSettingsCache = directProjectsSettings.getGeneralSettings().clone()
          var rootFiles = parameters.myFiles
          if (rootFiles == null) {
            rootFiles = listOf(LocalFileSystem.getInstance().findFileByNioFile(
              rootPath!!))
          }
          parameters.myGeneralSettingsCache!!.updateFromMavenConfig(rootFiles)
        }
      }
      return parameters.myGeneralSettingsCache
    }

  private fun readMavenProjectTree(process: MavenProgressIndicator) {
    val tree = MavenProjectsTree(projectOrDefault)
    tree.addManagedFilesWithProfiles(parameters.myFiles, MavenExplicitProfiles.NONE)
    tree.updateAll(false, generalSettings, process.indicator)
    parameters.myMavenProjectTree = tree
    parameters.mySelectedProjects = tree.rootProjects
  }

  private fun setRootDirectory(projectToUpdate: Project?, root: Path): Boolean {
    parameters.myFiles = null
    parameters.myMavenProjectTree = null

    // We cannot determinate project in non-EDT thread.
    parameters.myProjectToUpdate = projectToUpdate ?: ProjectManager.getInstance().defaultProject
    return runConfigurationProcess(MavenProjectBundle.message("maven.scanning.projects"), object : MavenTask {
      @Throws(MavenProcessCanceledException::class)
      override fun run(indicator: MavenProgressIndicator) {
        indicator.setText(MavenProjectBundle.message("maven.locating.files"))
        parameters.myImportRootDirectory = root
        if (parameters.myImportRootDirectory == null) {
          throw MavenProcessCanceledException()
        }
        parameters.myFiles = getProjectFiles(indicator)
        readMavenProjectTree(indicator)
        indicator.setText("")
        indicator.setText2("")
      }
    })
  }

  private fun selectProjectsToUpdate(): Boolean {
    val parameters = parameters
    val projectsTree = parameters.myMavenProjectTree
    val projects = projectsTree!!.rootProjects
    if (projects.isEmpty()) return false
    parameters.mySelectedProjects = projects
    return true
  }

  private fun setupProjectImport(project: Project): Boolean {
    val rootDirectory = rootPath
    return rootDirectory != null && setRootDirectory(project, rootDirectory) && selectProjectsToUpdate()
  }

  private fun showGeneralSettingsConfigurationDialog(project: Project,
                                                     generalSettings: MavenGeneralSettings,
                                                     runImportAfter: Runnable) {
    val dialog = MavenEnvironmentSettingsDialog(project, generalSettings, runImportAfter)
    ApplicationManager.getApplication().invokeLater { dialog.show() }
  }

  suspend fun commit(project: Project,
                     model: ModifiableModuleModel?,
                     modulesProvider: ModulesProvider?,
                     artifactModel: ModifiableArtifactModel?): List<Module?>? {
    val isVeryNewProject = project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) === java.lang.Boolean.TRUE
    val importingSettings = importingSettings
    if (isVeryNewProject) {
      ExternalProjectsManagerImpl.setupCreatedProject(project)
      MavenProjectsManager.setupCreatedMavenProject(importingSettings!!)
    }
    if (ApplicationManager.getApplication().isDispatchThread) {
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    MavenUtil.setupProjectSdk(project)
    val projectsNavigator = MavenProjectsNavigator.getInstance(project)
    if (projectsNavigator != null) projectsNavigator.groupModules = true
    if (!setupProjectImport(project)) {
      LOG.debug(String.format("Cannot import project for %s", project.toString()))
      return emptyList<Module>()
    }
    val settings = MavenWorkspaceSettingsComponent.getInstance(project).settings
    val generalSettings = generalSettings
    settings.setGeneralSettings(generalSettings)
    settings.setImportingSettings(importingSettings)
    val settingsFile = System.getProperty("idea.maven.import.settings.file")
    if (!StringUtil.isEmptyOrSpaces(settingsFile)) {
      settings.getGeneralSettings().setUserSettingsFile(settingsFile.trim { it <= ' ' })
    }
    val distributionUrl = getWrapperDistributionUrl(project.guessProjectDir())
    if (distributionUrl != null) {
      settings.getGeneralSettings().mavenHome = MavenServerManager.WRAPPED_MAVEN
    }
    val selectedProfiles = MavenExplicitProfiles.NONE.clone()
    val enabledProfilesList = System.getProperty("idea.maven.import.enabled.profiles")
    val disabledProfilesList = System.getProperty("idea.maven.import.disabled.profiles")
    if (enabledProfilesList != null || disabledProfilesList != null) {
      appendProfilesFromString(selectedProfiles.enabledProfiles, enabledProfilesList)
      appendProfilesFromString(selectedProfiles.disabledProfiles, disabledProfilesList)
    }
    val manager = MavenProjectsManager.getInstance(project)
    val selectedProjects: List<MavenProject> = ArrayList(
      parameters.mySelectedProjects)
    return performImport(project, model, modulesProvider, artifactModel, selectedProfiles, selectedProjects,
                         importingSettings, generalSettings)
  }

  private suspend fun performImport(project: Project,
                                    model: ModifiableModuleModel?,
                                    modulesProvider: ModulesProvider?,
                                    artifactModel: ModifiableArtifactModel?,
                                    selectedProfiles: MavenExplicitProfiles,
                                    selectedProjects: List<MavenProject>,
                                    importingSettings: MavenImportingSettings?,
                                    generalSettings: MavenGeneralSettings?): List<Module?>? {
    val manager = MavenProjectsManager.getInstance(project)
    val isVeryNewProject = project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) === java.lang.Boolean.TRUE
    manager.setIgnoredState(selectedProjects, false)
    if (MavenUtil.isLinearImportEnabled()) {
      MavenLog.LOG.warn("performImport: Linear Import is enabled")
      val dummy = MavenImportingManager.getInstance(project).openProjectAndImport(
        FilesList(MavenUtil.collectFiles(selectedProjects)),
        importingSettings!!,
        generalSettings!!,
        MavenImportSpec.EXPLICIT_IMPORT
      ).previewModulesCreated
      return if (dummy != null) {
        listOf(dummy)
      }
      else {
        emptyList<Module>()
      }
    }
    MavenLog.LOG.warn("performImport: Linear Import is disabled")
    if (isVeryNewProject && Registry.`is`("maven.create.dummy.module.on.first.import")) {
      val previewModule = createPreviewModule(project, selectedProjects)
      return manager.addManagedFilesWithProfilesAndUpdate(MavenUtil.collectFiles(selectedProjects), selectedProfiles, previewModule)
      //return listOf(previewModule)
    }
    else {
      return manager.addManagedFilesWithProfilesAndUpdate(MavenUtil.collectFiles(selectedProjects), selectedProfiles, null)
    }
  }

  private fun createPreviewModule(project: Project, selectedProjects: List<MavenProject>): Module? {
    if (ModuleManager.getInstance(project).modules.size == 0) {
      val root = ContainerUtil.getFirstItem(selectedProjects)
                 ?: return null
      val contentRoot = root.directoryFile
      return MavenImportUtil.createPreviewModule(project, contentRoot)
    }
    return null
  }

  companion object {
    private val LOG = Logger.getInstance(
      MavenProjectAsyncBuilder::class.java)
    private val currentProject: Project?
      private get() = CommonDataKeys.PROJECT.getData(DataManager.getInstance().dataContext)

    private fun runConfigurationProcess(message: @NlsContexts.DialogTitle String?, p: MavenTask): Boolean {
      try {
        MavenUtil.run(message, p)
      }
      catch (e: MavenProcessCanceledException) {
        return false
      }
      return true
    }

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
