package org.jetbrains.idea.maven.performancePlugin

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.performancePlugin.dto.MavenGoalConfigurationDto
import org.jetbrains.idea.maven.performancePlugin.utils.MavenConfigurationUtils.createRunnerParams


/**
 * The command executes a maven goals in module
 * Argument is serialized [MavenGoalConfigurationDto] as json
 * @see [MavenConstants.PHASES]
 */
class ExecuteMavenGoalCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = "%executeMavenGoals"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val promise = AsyncPromise<Any?>()
    val settings = deserializeOptionsFromJson(extractCommandArgument(PREFIX), MavenGoalConfigurationDto::class.java)
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

    ApplicationManager.getApplication().invokeLater {
      try {
        val params = createRunnerParams(project, settings)
        MavenRunConfigurationType.runConfiguration(project, params, null)
      }
      catch (t: Throwable) {
        promise.setError(t)
      }

    }
    return promise
  }
}