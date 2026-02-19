package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.lambda.testFramework.testApi.utils.tryTimes
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.jetbrains.performancePlugin.commands.CodeAnalysisStateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds


context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForCodeAnalysisToFinish(additionalAwaits: suspend () -> Unit = {}) {
  val taskDescription = "Waiting for code analysis to finish"
  val project = getProject()
  runLogged(taskDescription) {
    withContext(Dispatchers.Default) {
      val analyserListenerState = project.service<CodeAnalysisStateListener>()
      var whileSmartModeRunner: Job? = null
      val maximumTimeForCodeAnalysisIfNoIndexing = 25.seconds // the max time we expect the analysis to run when there is no indexing.
      val attempts = 8

      try {
        tryTimes(attempts = attempts,
                 description = taskDescription,
                 delay = 0.seconds,
                 action = {
                   whileSmartModeRunner = runWhileSmartMode(project)
                   analyserListenerState.waitAnalysisToFinish(maximumTimeForCodeAnalysisIfNoIndexing, throws = true, logsError = false)
                   additionalAwaits()
                   analyserListenerState.waitAnalysisToFinish(maximumTimeForCodeAnalysisIfNoIndexing, throws = true, logsError = false)
                   whileSmartModeRunner.cancel()
                 },
                 onAttemptFail = { t ->
                   try {
                     if (t is TimeoutException) {
                       if (whileSmartModeRunner!!.isActive) {
                         // It means we were running for `maximumTimeForCodeAnalysisIfNoIndexing`, and there was no indexing during that time,
                         // but analysis has not finished.
                         // It means the Code Analysis is probably hanging.
                         // Doesn't make sense to wait any longer.
                         throw IllegalStateException("Code Analysis is not ready for $maximumTimeForCodeAnalysisIfNoIndexing and there was no indexing during that time", t)
                       }
                     }
                     else {
                       throw t
                     }
                   }
                   finally {
                     whileSmartModeRunner?.cancel()
                   }
                 },
                 onFinalFail = {
                   whileSmartModeRunner?.cancel()
                 },
                 failureMessage = "We could not wait for Code Analysis in ${maximumTimeForCodeAnalysisIfNoIndexing * attempts} to finish, the probable cause is long indexing")
      }
      catch (t: Throwable) {
        frameworkLogger.error(t)
      }
    }
  }
}

/**
 * Will wait until canceled or dumb mode is triggered
 */
private fun CoroutineScope.runWhileSmartMode(project: Project): Job = launch(Dispatchers.IO) {
  while (!DumbService.getInstance(project).isDumb) {
    delay(1.seconds)
  }
}