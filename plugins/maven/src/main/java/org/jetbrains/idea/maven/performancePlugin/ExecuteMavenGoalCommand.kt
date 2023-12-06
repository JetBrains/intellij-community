package org.jetbrains.idea.maven.performancePlugin

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager


/**
 * The command executes a maven goals in module
 * Syntax: %executeMavenGoals moduleName [moduleName] goalNames [goal1,goal2,goal3]
 * Example: %executeMavenGoals moduleName main module goalNames verify,package
 * @see [MavenConstants.PHASES]
 */
class ExecuteMavenGoalCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = "%executeMavenGoals"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val promise = AsyncPromise<Any?>()
    val args = extractCommandArgument(PREFIX)
    val moduleName = "(?<=moduleName)(.*)(?=goalNames)".toRegex().find(args)?.value?.trim() ?: throw IllegalArgumentException(
      "${args} doesn't contain valid module with goal name")
    val goals = args.substringAfter("goalNames").trim().split(",")
    val project = context.getProject()
    project.messageBus.connect().subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
        if (cause != null) {
          promise.setError(cause)
        }
      }

      override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        if (exitCode != 0) {
          promise.setError("Process finished with code exit code $exitCode")
        }
        else {
          promise.setResult(null)
        }
      }
    })
    perform(project, moduleName, goals, promise)
    return promise
  }

  private fun perform(project: Project, moduleName: String, goals: List<String>, promise: AsyncPromise<Any?>) {
    ApplicationManager.getApplication().invokeLater {
      val projectsManager = MavenProjectsManager.getInstance(project)
      if (projectsManager == null) {
        promise.setError("There is no MavenProjectsManager for project")
        return@invokeLater
      }

      val currentProjects = projectsManager.projects
      val mavenProject = currentProjects.firstOrNull { it.displayName == moduleName }
      if (mavenProject == null) {
        promise.setError(
          "There is no module with name $moduleName. Actual modules: ${currentProjects.joinToString("\n") { it.displayName }}")
        return@invokeLater
      }

      val explicitProfiles = projectsManager.getExplicitProfiles()

      val params = MavenRunnerParameters(true,
                                         mavenProject.directory,
                                         mavenProject.file.getName(),
                                         goals,
                                         explicitProfiles.enabledProfiles,
                                         explicitProfiles.disabledProfiles)
      MavenRunConfigurationType.runConfiguration(project, params, null)
    }
  }
}