package org.jetbrains.idea.maven.performancePlugin

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.project.MavenProjectsManager


/**
 * The command executes a maven goal in module
 * Syntax: %executeMavenGoal [moduleName] [goal]
 * Example: %executeMavenGoal mainModule package
 */
class ExecuteMavenGoalCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = "%executeMavenGoal"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val promise = AsyncPromise<Any?>()
    val split = extractCommandArgument(PREFIX).split(" ")
    val moduleName = split[0]
    val goal = split[1]
    val project = context.getProject()
    project.messageBus.connect().subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
        if (cause != null) {
          promise.setError(cause)
        }
      }
    })

    perform(project, moduleName, goal) { descriptor ->
      descriptor?.processHandler?.addProcessListener(
        object : ProcessAdapter() {
          override fun processTerminated(event: ProcessEvent) {
            promise.setResult(null)
          }
        })
    }
    return promise
  }

  private fun perform(project: Project, moduleName: String, goal: String, callback: ProgramRunner.Callback) {
    ApplicationManager.getApplication().invokeLater {
      val projectsManager = MavenProjectsManager.getInstance(project)
      if (projectsManager == null) return@invokeLater

      val mavenProject = projectsManager.projects.first { it.displayName.equals(moduleName) }
      if (mavenProject == null) return@invokeLater

      val explicitProfiles = projectsManager.getExplicitProfiles()

      val params = MavenRunnerParameters(true,
                                         mavenProject.directory,
                                         mavenProject.file.getName(),
                                         listOf(goal),
                                         explicitProfiles.enabledProfiles,
                                         explicitProfiles.disabledProfiles)
      MavenRunConfigurationType.runConfiguration(project, params, callback)
    }
  }
}