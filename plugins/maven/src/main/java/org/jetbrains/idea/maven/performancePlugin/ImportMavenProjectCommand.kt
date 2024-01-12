// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.internal
import com.intellij.util.DisposeAwareRunnable
import com.intellij.workspaceModel.ide.JpsProjectLoadingManager
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.preimport.MavenProjectStaticImporter
import org.jetbrains.idea.maven.utils.MavenUtil

class ImportMavenProjectCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val project = context.getProject()
    return run {
      val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
      runWhenMavenImportAndIndexingFinished(context, { actionCallback.setDone() }, project)
      actionCallback.toPromise()
    }
  }


  private fun runWhenMavenImportAndIndexingFinished(context: PlaybackContext,
                                                    runnable: Runnable,
                                                    project: Project) {
    val projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(project)
    val currentAutoReloadType = projectTrackerSettings.autoReloadType
    projectTrackerSettings.autoReloadType = AutoReloadType.NONE
    context.message("Waiting for fully open and initialized maven project", line)
    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized {
      MavenUtil.runWhenInitialized(project) {
        JpsProjectLoadingManager.getInstance(project).jpsProjectLoaded {
          ApplicationManager.getApplication().executeOnPooledThread {
            context.message("Import of the project has been started", line)
            val mavenManager = MavenProjectsManager.getInstance(project)
            runBlockingMaybeCancellable {
              MavenProjectStaticImporter.setPreimport(false)
              if (!mavenManager.isMavenizedProject) {
                val files = mavenManager.collectAllAvailablePomFiles()
                mavenManager.addManagedFilesWithProfilesAndUpdate(files, MavenExplicitProfiles.NONE, null, null)
              }
              else {
                mavenManager.updateAllMavenProjects(MavenImportSpec.EXPLICIT_IMPORT)
              }
            }
            context.message("Import of the maven project has been finished", line)
            projectTrackerSettings.autoReloadType = currentAutoReloadType
            DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(runnable, project))
            val storageVersion = WorkspaceModel.getInstance(project).internal.entityStorage.version
            val storage = WorkspaceModel.getInstance(project).currentSnapshot
            //val sourceRoots = storage.entities(SourceRootEntity::class.java).map { it.url.url }.toList()
            context.message("Entity storage version: $storageVersion, snapshot: $storage", line)
            //context.message("source roots: $sourceRoots", line)
          }
        }
      }
    }
  }

  companion object {
    const val PREFIX = "%importMavenProject"
  }
}