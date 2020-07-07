// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.externalSystem.autoimport.*
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService

class MavenProjectsAware(
  project: Project,
  private val projectsTree: MavenProjectsTree,
  private val manager: MavenProjectsManager,
  private val watcher: MavenProjectsManagerWatcher,
  private val backgroundExecutor: ExecutorService
) : ExternalSystemProjectAware {

  private val isImportCompleted = AtomicBooleanProperty(true)

  override val projectId = ExternalSystemProjectId(MavenUtil.SYSTEM_ID, project.name)

  override val settingsFiles: Set<String>
    get() = collectSettingsFiles().map { FileUtil.toCanonicalPath(it) }.toSet()

  override fun subscribe(listener: ExternalSystemProjectRefreshListener, parentDisposable: Disposable) {
    isImportCompleted.afterReset({ listener.beforeProjectRefresh() }, parentDisposable)
    isImportCompleted.afterSet({ listener.afterProjectRefresh(SUCCESS) }, parentDisposable)
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    FileDocumentManager.getInstance().saveAllDocuments()
    val settingsFilesContext = context.settingsFilesContext
    if (context.hasUndefinedModifications) {
      manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
    }
    else {
      submitSettingsFilesPartition(settingsFilesContext) { (filesToUpdate, filesToDelete) ->
        val updated = settingsFilesContext.created + settingsFilesContext.updated
        val deleted = settingsFilesContext.deleted
        if (updated.size == filesToUpdate.size && deleted.size == filesToDelete.size) {
          watcher.scheduleUpdate(filesToUpdate, filesToDelete, false, true)
        }
        else {
          manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
        }
      }
    }
  }

  private fun submitSettingsFilesPartition(
    context: ExternalSystemSettingsFilesReloadContext,
    action: (Pair<List<VirtualFile>, List<VirtualFile>>) -> Unit
  ) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      action(partitionSettingsFiles(context))
      return
    }
    ReadAction.nonBlocking(Callable { partitionSettingsFiles(context) })
      .expireWith(manager)
      .finishOnUiThread(ModalityState.defaultModalityState(), action)
      .submit(backgroundExecutor)
  }

  private fun partitionSettingsFiles(context: ExternalSystemSettingsFilesReloadContext): Pair<List<VirtualFile>, List<VirtualFile>> {
    val localFileSystem = LocalFileSystem.getInstance()
    val created = context.created.mapNotNull { localFileSystem.findFileByPath(it) }
    val projectsFiles = projectsTree.projectsFiles
    val updated = projectsFiles.filter { it.path in context.updated }
    val deleted = projectsFiles.filter { it.path in context.deleted }
    return created + updated to deleted
  }

  private fun hasPomFile(rootDirectory: String): Boolean {
    return MavenConstants.POM_NAMES.asSequence()
      .map { join(rootDirectory, it) }
      .any { projectsTree.isPotentialProject(it) }
  }

  private fun collectSettingsFiles() = sequence {
    yieldAll(projectsTree.managedFilesPaths)
    yieldAll(projectsTree.projectsFiles.map { it.path })
    for (mavenProject in projectsTree.projects) {
      ProgressManager.checkCanceled()

      val rootDirectory = mavenProject.directory
      yieldAll(mavenProject.modulePaths)
      yield(join(rootDirectory, MavenConstants.JVM_CONFIG_RELATIVE_PATH))
      yield(join(rootDirectory, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH))
      yield(join(rootDirectory, MavenConstants.MAVEN_WRAPPER_RELATIVE_PATH))
      if (hasPomFile(rootDirectory)) {
        yield(join(rootDirectory, MavenConstants.PROFILES_XML))
      }
    }
  }

  private fun join(parentPath: String, relativePath: String) = File(parentPath, relativePath).path

  init {
    manager.addManagerListener(object : MavenProjectsManager.Listener {
      override fun importAndResolveScheduled() = isImportCompleted.set(false)
      override fun projectImportCompleted() = isImportCompleted.set(true)
    })
  }
}
