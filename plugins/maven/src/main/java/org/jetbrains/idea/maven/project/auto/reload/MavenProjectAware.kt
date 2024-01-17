// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectListener
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectReloadContext
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.concurrent.ExecutorService

@ApiStatus.Internal
class MavenProjectAware(
  private val project: Project,
  override val projectId: ExternalSystemProjectId,
  private val manager: MavenProjectsManager,
  private val backgroundExecutor: ExecutorService
) : ExternalSystemProjectAware {

  private val isImportCompleted = AtomicBooleanProperty(true)

  override val settingsFiles: Set<String>
    get() = collectSettingsFiles()

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    isImportCompleted.afterReset(parentDisposable) { listener.onProjectReloadStart() }
    isImportCompleted.afterSet(parentDisposable) { listener.onProjectReloadFinish(SUCCESS) }
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    ApplicationManager.getApplication().invokeAndWait {
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    if (context.hasUndefinedModifications) {
      manager.findAllAvailablePomFilesIfNotMavenized()
      manager.scheduleUpdateAllMavenProjects(MavenImportSpec(true, context.isExplicitReload))
    }
    else {
      val settingsFilesContext = context.settingsFilesContext

      val filesToUpdate = mutableListOf<VirtualFile>()
      val filesToDelete = mutableListOf<VirtualFile>()
      for (projectsFile in manager.projectsTree.projectsFiles) {
        val path = projectsFile.path
        if (path in settingsFilesContext.created) filesToUpdate.add(projectsFile)
        if (path in settingsFilesContext.updated) filesToUpdate.add(projectsFile)
        if (path in settingsFilesContext.deleted) filesToDelete.add(projectsFile)
      }

      val updated = settingsFilesContext.created + settingsFilesContext.updated
      val deleted = settingsFilesContext.deleted

      if (updated.size == filesToUpdate.size && deleted.size == filesToDelete.size) {
        manager.scheduleUpdateMavenProjects(MavenImportSpec(true, context.isExplicitReload), filesToUpdate, filesToDelete)
      }
      else {
        // IDEA-276087 if changed not project files(.mvn) then run full import
        manager.findAllAvailablePomFilesIfNotMavenized()
        manager.scheduleUpdateAllMavenProjects(MavenImportSpec(false, context.isExplicitReload))
      }
    }
  }

  private fun hasPomFile(rootDirectory: String): Boolean {
    val projectTree = manager.projectsTree
    return MavenConstants.POM_NAMES.any {
      projectTree.isPotentialProject("$rootDirectory/$it")
    }
  }

  private fun collectSettingsFiles(): Set<String> {
    val result = LinkedHashSet<String>()
    result.addAll(manager.projectsTree.managedFilesPaths)
    result.addAll(manager.projectsTree.projectsFiles.map { it.path })
    for (mavenProject in manager.projectsTree.projects) {
      ProgressManager.checkCanceled()

      result.addAll(mavenProject.modulePaths)
      val rootDirectory = mavenProject.directory
      result.add(rootDirectory + "/" + MavenConstants.JVM_CONFIG_RELATIVE_PATH)
      result.add(rootDirectory + "/" + MavenConstants.MAVEN_CONFIG_RELATIVE_PATH)
      result.add(rootDirectory + "/" + MavenConstants.MAVEN_WRAPPER_RELATIVE_PATH)
      if (hasPomFile(rootDirectory)) {
        result.add(rootDirectory + "/" + MavenConstants.PROFILES_XML)
      }
    }
    return result
  }

  init {
    project.messageBus.connect(manager)
      .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
        override fun importFinished(importedProjects: MutableCollection<MavenProject>, newModules: MutableList<Module>) {
          isImportCompleted.set(true)
        }

        override fun importStarted() {
          isImportCompleted.set(false)
        }
      })
  }
}
