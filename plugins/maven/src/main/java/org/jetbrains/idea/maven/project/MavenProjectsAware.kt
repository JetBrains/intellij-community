// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.*
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.externalSystem.autoimport.settings.ReadAsyncSupplier
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.util.concurrent.ExecutorService

class MavenProjectsAware(
  project: Project,
  private val manager: MavenProjectsManager,
  private val watcher: MavenProjectsManagerWatcher,
  private val backgroundExecutor: ExecutorService
) : ExternalSystemProjectAware {

  private val isImportCompleted = AtomicBooleanProperty(true)

  override val projectId = ExternalSystemProjectId(MavenUtil.SYSTEM_ID, project.name)

  override val settingsFiles: Set<String>
    get() = collectSettingsFiles().map { FileUtil.toCanonicalPath(it) }.toSet()

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    isImportCompleted.afterReset(parentDisposable) { listener.onProjectReloadStart() }
    isImportCompleted.afterSet(parentDisposable) { listener.onProjectReloadFinish(SUCCESS) }
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    FileDocumentManager.getInstance().saveAllDocuments()
    val settingsFilesContext = context.settingsFilesContext
    if (context.hasUndefinedModifications) {
      manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles(MavenImportSpec(true, true, context.isExplicitReload))
    }
    else {
      submitSettingsFilesPartition(settingsFilesContext) { (filesToUpdate, filesToDelete) ->
        val updated = settingsFilesContext.created + settingsFilesContext.updated
        val deleted = settingsFilesContext.deleted
        if (updated.size == filesToUpdate.size && deleted.size == filesToDelete.size) {
          watcher.scheduleUpdate(filesToUpdate, filesToDelete, MavenImportSpec(false, true, context.isExplicitReload))
        }
        else {
          manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles(MavenImportSpec(false, true, context.isExplicitReload))
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
    return MavenConstants.POM_NAMES.asSequence()
      .map { join(rootDirectory, it) }
      .any { manager.projectsTree.isPotentialProject(it) }
  }


  private fun collectSettingsFiles() = sequence {
    yieldAll( manager.projectsTree.managedFilesPaths)
    yieldAll( manager.projectsTree.projectsFiles.map { it.path })
    for (mavenProject in manager.projectsTree.projects) {
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
    project.messageBus.connect(manager)
      .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
        override fun importFinished(importedProjects: MutableCollection<MavenProject>, newModules: MutableList<Module>) {
          isImportCompleted.set(true)
        }

        override fun importStarted(spec: MavenImportSpec?) {
          isImportCompleted.set(false)
        }
      })
  }
}
