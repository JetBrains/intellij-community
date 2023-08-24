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
import com.intellij.util.DisposeAwareRunnable
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.importing.FilesList
import org.jetbrains.idea.maven.project.importing.MavenImportingManager
import org.jetbrains.idea.maven.utils.MavenUtil

class ImportMavenProjectCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val project = context.getProject()
    return if (MavenUtil.isLinearImportEnabled()) {
      runLinearMavenImport(context, project)
    }
    else {
      val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
      runWhenMavenImportAndIndexingFinished(context, { actionCallback.setDone() }, project)
      actionCallback.toPromise()
    }
  }

  private fun runLinearMavenImport(context: PlaybackContext, project: Project): Promise<Any?> {
    val projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(project)
    val currentAutoReloadType = projectTrackerSettings.autoReloadType
    projectTrackerSettings.autoReloadType = AutoReloadType.NONE
    context.message("Waiting for fully open and initialized maven project", line)
    context.message("Import of the project has been started", line)
    val result = AsyncPromise<Any?>()
    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized {
      val mavenManager = MavenProjectsManager.getInstance(project)
      MavenImportingManager.getInstance(project).openProjectAndImport(
        FilesList(mavenManager.collectAllAvailablePomFiles())
      ).finishPromise
        .onSuccess {
          context.message("Import of the maven project has been finished", line)
          projectTrackerSettings.autoReloadType = currentAutoReloadType
          DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create({ result.setResult(it) }, project))
        }
        .onError {
          result.setError(it!!)
        }
    }
    return result
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
        ApplicationManager.getApplication().executeOnPooledThread {
          waitForCurrentMavenImportActivities(context, project)
          context.message("Import of the project has been started", line)
          val mavenManager = MavenProjectsManager.getInstance(project)
          if (!mavenManager.isMavenizedProject) {
            mavenManager.addManagedFiles(mavenManager.collectAllAvailablePomFiles())
          }
          runBlockingMaybeCancellable {
            mavenManager.updateAllMavenProjects(MavenImportSpec.EXPLICIT_IMPORT)
          }
          waitForCurrentMavenImportActivities(context, project)
          context.message("Import of the maven project has been finished", line)
          projectTrackerSettings.autoReloadType = currentAutoReloadType
          DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(runnable, project))
        }
      }
    }
  }

  private fun waitForCurrentMavenImportActivities(context: PlaybackContext, project: Project) {
    context.message("Waiting for current maven import activities", line)
    MavenProjectsManager.getInstance(project).waitForImportCompletion()
    context.message("Maven import activities completed", line)
  }

  companion object {
    const val PREFIX = "%importMavenProject"
  }
}