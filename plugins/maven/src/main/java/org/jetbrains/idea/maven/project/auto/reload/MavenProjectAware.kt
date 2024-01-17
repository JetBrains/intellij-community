// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.autoimport.*
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.externalSystem.autoimport.settings.ReadAsyncSupplier
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
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
    val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
    if (context.hasUndefinedModifications) {
      cs.launch {
        manager.findAllAvailablePomFilesIfNotMavenized()
        manager.updateAllMavenProjects(MavenImportSpec(true, context.isExplicitReload))
      }
    }
    else {
      val settingsFilesContext = context.settingsFilesContext
      submitSettingsFilesPartition(settingsFilesContext) { (filesToUpdate, filesToDelete) ->
        val updated = settingsFilesContext.created + settingsFilesContext.updated
        val deleted = settingsFilesContext.deleted
        if (updated.size == filesToUpdate.size && deleted.size == filesToDelete.size) {
          cs.launch {
            manager.updateMavenProjects(MavenImportSpec(true, context.isExplicitReload), filesToUpdate, filesToDelete)
          }
        }
        else {
          cs.launch {
            manager.findAllAvailablePomFilesIfNotMavenized()
            manager.updateAllMavenProjects(MavenImportSpec(false, context.isExplicitReload))
          }
        }
      }
    }
  }

  private fun submitSettingsFilesPartition(
    context: ExternalSystemSettingsFilesReloadContext,
    action: (Pair<List<VirtualFile>, List<VirtualFile>>) -> Unit
  ) {
    ReadAsyncSupplier.Builder { partitionSettingsFiles(context) }
      .build(backgroundExecutor)
      .supply(manager, action)
  }

  private fun partitionSettingsFiles(context: ExternalSystemSettingsFilesReloadContext): Pair<List<VirtualFile>, List<VirtualFile>> {
    val updated = mutableListOf<VirtualFile>()
    val deleted = mutableListOf<VirtualFile>()
    for (projectsFile in manager.projectsTree.projectsFiles) {
      val path = projectsFile.path
      if (path in context.created) updated.add(projectsFile)
      if (path in context.updated) updated.add(projectsFile)
      if (path in context.deleted) deleted.add(projectsFile)
    }
    return updated to deleted
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
