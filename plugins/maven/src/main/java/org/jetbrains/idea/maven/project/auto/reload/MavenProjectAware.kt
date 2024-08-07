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
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSyncListener
import org.jetbrains.idea.maven.utils.MavenLog

@ApiStatus.Internal
class MavenProjectAware(
  private val myProject: Project,
  override val projectId: ExternalSystemProjectId,
  private val manager: MavenProjectsManager
) : ExternalSystemProjectAware {

  private val isSyncCompleted = AtomicBooleanProperty(true)

  override val settingsFiles: Set<String>
    get() = collectSettingsFiles()

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    isSyncCompleted.afterReset(parentDisposable) { listener.onProjectReloadStart() }
    isSyncCompleted.afterSet(parentDisposable) { listener.onProjectReloadFinish(SUCCESS) }
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    MavenLog.LOG.debug("MavenProjectAware.reloadProject")
    ApplicationManager.getApplication().invokeAndWait {
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    if (context.hasUndefinedModifications) {
      MavenLog.LOG.debug("MavenProjectAware.reloadProject - context.hasUndefinedModifications=true")
      val spec = MavenSyncSpec.incremental("MavenProjectAware.reloadProject, undefined modifications", context.isExplicitReload)
      manager.scheduleUpdateAllMavenProjects(spec)
    }
    else {
      MavenLog.LOG.debug("MavenProjectAware.reloadProject - context.hasUndefinedModifications=false")
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
        val spec = MavenSyncSpec.incremental("MavenProjectAware.reloadProject, sync selected", context.isExplicitReload)
        manager.scheduleUpdateMavenProjects(spec, filesToUpdate, filesToDelete)
      }
      else {
        val spec = MavenSyncSpec.incremental("MavenProjectAware.reloadProject, sync all", context.isExplicitReload)
        manager.scheduleUpdateAllMavenProjects(spec)
      }
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
    }
    return result
  }

  init {
    ApplicationManager.getApplication().messageBus.connect(manager)
      .subscribe(MavenSyncListener.TOPIC, object : MavenSyncListener {
        override fun syncFinished(project: Project) {
          if (myProject == project) {
            isSyncCompleted.set(true)
          }
        }

        override fun syncStarted(project: Project) {
          if (myProject == project) {
            isSyncCompleted.set(false)
          }
        }
      })
  }
}
