package org.jetbrains.idea.maven.performancePlugin

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommand
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.execution.MavenRunAnythingProvider
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.performancePlugin.dto.MavenGoalConfigurationDto
import org.jetbrains.idea.maven.performancePlugin.utils.MavenConfigurationUtils.createRunnerParams


/**
 * The command executes a maven goals in module.
 * Argument is serialized [MavenGoalConfigurationDto] as json
 * runAnything false - executes like double click form lifecycle
 * runAnything true - executes like double control and execute mvn [goals]
 * @see [MavenConstants.PHASES]
 */
class ExecuteMavenGoalCommand(text: String, line: Int) : PerformanceCommand(text, line) {
  companion object {
    const val NAME = "executeMavenGoals"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  private fun executeGoalAsRunAnything(project: Project, settings: MavenGoalConfigurationDto) {
    val context = SimpleDataContext.builder()
      .add<Project>(CommonDataKeys.PROJECT, project)
      .build()
    MavenRunAnythingProvider().execute(context, "mvn ${settings.goals.joinToString(" ")}")
  }

  private fun executeGoalsFromLifecycle(project: Project, settings: MavenGoalConfigurationDto) {
    val params = createRunnerParams(project, settings)
    MavenRunConfigurationType.runConfiguration(project, params, null)
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val project = context.getProject()
    val promise = AsyncPromise<Any?>()
    val settings = deserializeOptionsFromJson(extractCommandArgument(PREFIX), MavenGoalConfigurationDto::class.java)

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


    ApplicationManager.getApplication().invokeLater {
      try {
        if (settings.runAnything)
          executeGoalAsRunAnything(project, settings)
        else
          executeGoalsFromLifecycle(project, settings)
      }
      catch (t: Throwable) {
        promise.setError(t)
      }

    }
    return promise
  }

  override fun getName(): String {
    return NAME
  }
}