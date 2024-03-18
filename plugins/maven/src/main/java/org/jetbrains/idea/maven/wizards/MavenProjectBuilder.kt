// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.IdeUIModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.projectImport.DeprecatedProjectBuilderForImport
import com.intellij.projectImport.ProjectImportBuilder
import com.intellij.projectImport.ProjectOpenProcessor
import icons.OpenapiIcons
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.project.actions.LookForNestedToggleAction
import org.jetbrains.idea.maven.utils.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.swing.Icon

private val LOG = Logger.getInstance(MavenProjectBuilder::class.java)

/**
 * Do not use this project import builder directly.
 *
 *
 * Internal stable Api
 * Use [com.intellij.ide.actions.ImportModuleAction.createFromWizard] to import (attach) a new project.
 * Use [com.intellij.ide.impl.ProjectUtil.openOrImport] to open (import) a new project.
 */
@Deprecated("use MavenProjectAsyncBuilder")
class MavenProjectBuilder : ProjectImportBuilder<MavenProject>(), DeprecatedProjectBuilderForImport {

  private class Parameters {
    var myProjectToUpdate: Project? = null

    var myGeneralSettingsCache: MavenGeneralSettings? = null
    var myImportingSettingsCache: MavenImportingSettings? = null
    var myImportRootDirectory: Path? = null
    var myImportProjectFile: VirtualFile? = null
    var myFiles: List<VirtualFile?>? = null

    var myMavenProjectTree: MavenProjectsTree? = null
    var mySelectedProjects: List<MavenProject>? = null

    var myOpenModulesConfigurator: Boolean = false
  }

  private var myParameters: Parameters? = null

  override fun getName(): String {
    return MavenProjectBundle.message("maven.name")
  }

  override fun getIcon(): Icon {
    return OpenapiIcons.RepositoryLibraryLogo
  }

  override fun cleanup() {
    myParameters = null
    super.cleanup()
  }

  override fun isSuitableSdkType(sdk: SdkTypeId): Boolean {
    return sdk === JavaSdk.getInstance()
  }

  private val parameters: Parameters
    get() {
      if (myParameters == null) {
        myParameters = Parameters()
      }
      return myParameters!!
    }

  val projectFileToImport: VirtualFile?
    get() {
      val projectFile = parameters.myImportProjectFile
      if (null != projectFile) return projectFile

      val importRootDirectory = parameters.myImportRootDirectory
      if (null != importRootDirectory) {
        return VirtualFileManager.getInstance().findFileByNioPath(importRootDirectory)
      }

      return null
    }

  private fun commitWithAsyncBuilder(project: Project,
                                     model: ModifiableModuleModel?,
                                     modulesProvider: ModulesProvider,
                                     artifactModel: ModifiableArtifactModel?): List<Module> {
    val projectFile = projectFileToImport
    if (null == projectFile) {
      LOG.warn("Project file missing")
      return listOf()
    }

    var modelsProvider: IdeUIModifiableModelsProvider? = null
    if (model != null) {
      modelsProvider = IdeUIModifiableModelsProvider(project, model, modulesProvider as ModulesConfigurator, artifactModel)
    }

    return MavenProjectAsyncBuilder().commitSync(project, projectFile, modelsProvider)
  }


  override fun commit(project: Project,
                      model: ModifiableModuleModel?,
                      modulesProvider: ModulesProvider,
                      artifactModel: ModifiableArtifactModel?): List<Module> {
    return commitWithAsyncBuilder(project, model, modulesProvider, artifactModel)
  }


  @Deprecated("Use {@link #setRootDirectory(Project, Path)}")
  fun setRootDirectory(projectToUpdate: Project?, root: String): Boolean {
    return setRootDirectory(projectToUpdate, Paths.get(root))
  }

  private fun runConfigurationProcess(message: @NlsContexts.DialogTitle String?, p: MavenTask): Boolean {
    try {
      MavenUtil.run(message, p)
    }
    catch (e: MavenProcessCanceledException) {
      return false
    }
    return true
  }

  fun setRootDirectory(projectToUpdate: Project?, root: Path): Boolean {
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

  private fun readMavenProjectTree(process: MavenProgressIndicator) {
    val tree = MavenProjectsTree(projectOrDefault)
    tree.addManagedFilesWithProfiles(parameters.myFiles, MavenExplicitProfiles.NONE)

    runBlockingMaybeCancellable {
      tree.updateAll(false, generalSettings, process.indicator)
    }

    parameters.myMavenProjectTree = tree
    parameters.mySelectedProjects = tree.rootProjects
  }

  override fun getList(): List<MavenProject>? {
    return parameters.myMavenProjectTree!!.rootProjects
  }

  override fun setList(projects: List<MavenProject>) {
    parameters.mySelectedProjects = projects
  }

  override fun isMarked(element: MavenProject): Boolean {
    return parameters.mySelectedProjects!!.contains(element)
  }

  override fun isOpenProjectSettingsAfter(): Boolean {
    return parameters.myOpenModulesConfigurator
  }

  override fun setOpenProjectSettingsAfter(on: Boolean) {
    parameters.myOpenModulesConfigurator = on
  }

  private val generalSettings: MavenGeneralSettings
    get() {
      var settings = parameters.myGeneralSettingsCache
      if (settings == null) {
        settings = ApplicationManager.getApplication().runReadAction(Computable {
          val newSettings = directProjectsSettings.generalSettings.clone()
          var rootFiles = parameters.myFiles
          if (rootFiles == null) {
            rootFiles = listOf(LocalFileSystem.getInstance().findFileByNioFile(
              rootPath!!))
          }
          newSettings.updateFromMavenConfig(rootFiles)
          newSettings
        })
        parameters.myGeneralSettingsCache = settings;
        return settings
      }
      else {
        return settings
      }

    }

  val importingSettings: MavenImportingSettings?
    get() {
      if (parameters.myImportingSettingsCache == null) {
        ApplicationManager.getApplication().runReadAction {
          parameters.myImportingSettingsCache = directProjectsSettings.importingSettings.clone()
        }
      }
      return parameters.myImportingSettingsCache
    }

  private val directProjectsSettings: MavenWorkspaceSettings
    get() {
      ApplicationManager.getApplication().assertReadAccessAllowed()

      var project = if (isUpdate) projectToUpdate else null
      if (project == null || project.isDisposed) project = ProjectManager.getInstance().defaultProject

      return MavenWorkspaceSettingsComponent.getInstance(project).settings
    }

  fun setFiles(files: List<VirtualFile?>?) {
    parameters.myFiles = files
  }

  val projectToUpdate: Project?
    get() {
      if (parameters.myProjectToUpdate == null) {
        parameters.myProjectToUpdate = getCurrentProject()
      }
      return parameters.myProjectToUpdate
    }

  val projectOrDefault: Project
    get() {
      var project = projectToUpdate
      if (project == null || project.isDisposed) project = ProjectManager.getInstance().defaultProject
      return project
    }

  val rootPath: Path?
    get() {
      if (parameters.myImportRootDirectory == null && isUpdate) {
        val project = projectToUpdate
        parameters.myImportRootDirectory = if (project == null) null else Paths.get(Objects.requireNonNull(project.basePath))
      }
      return parameters.myImportRootDirectory
    }

  override fun setFileToImport(path: String) {
    setFileToImport(Paths.get(path))
  }

  fun setFileToImport(path: Path) {
    parameters.myImportRootDirectory = if (Files.isDirectory(path)) path else path.parent
  }

  fun setFileToImport(file: VirtualFile) {
    if (!file.isDirectory) parameters.myImportProjectFile = file
    parameters.myImportRootDirectory = if (file.isDirectory) file.toNioPath() else file.parent.toNioPath()
  }

  override fun createProject(name: String, path: String): Project? {
    val project = super.createProject(name, path)
    if (project != null) {
      ExternalProjectsManagerImpl.setupCreatedProject(project)
      project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, true)
    }
    return project
  }

  override fun getProjectOpenProcessor(): ProjectOpenProcessor {
    return ProjectOpenProcessor.EXTENSION_POINT_NAME.findExtensionOrFail(MavenProjectOpenProcessor::class.java)
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
}
