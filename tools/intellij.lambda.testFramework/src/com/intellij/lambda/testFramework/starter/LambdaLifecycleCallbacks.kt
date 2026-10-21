package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.coroutine.CommonScope.testSuiteSupervisorScope
import com.intellij.ide.starter.utils.catchAll
import com.intellij.idea.IdeaLogger
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.remoteDev.tests.impl.utils.getArtifactsFileName
import com.intellij.remoteDev.tests.impl.utils.getTimeoutHonouringDebug
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import com.jetbrains.rd.util.reactive.RdFault
import com.jetbrains.rd.util.string.printToString
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

/** An IDE-side JUnit lifecycle callback that [LambdaReusedSessionStrategy] runs in the lambda IDE. */
internal enum class LambdaLifecycleCallback(val displayName: String) {
  BEFORE_ALL("Before all"),
  BEFORE_EACH("Before each"),
  AFTER_EACH("After each"),
  AFTER_ALL("After all"),
}

/**
 * Runs [callback] in the lambda IDE of [context].
 *
 * A failure stops the session, so that no later test runs on an IDE whose lifecycle is broken. An after-callback
 * only reports an IDE that is gone. A failed callback stops the session, and a failure here would hide that one.
 */
internal fun runLambdaLifecycleCallback(context: ExtensionContext, callback: LambdaLifecycleCallback) {
  val callbackName = callback.displayName
  val contextName = buildString {
    append(context.requiredTestClass.name)
    context.testMethod.getOrNull()?.let { append(".${it.name}") }
    append(" ")
    append(context.displayName)
  }

  val currentIde = LambdaIdeSession.currentOrNull(context)
  if (currentIde == null) {
    if (callback == LambdaLifecycleCallback.AFTER_EACH || callback == LambdaLifecycleCallback.AFTER_ALL) {
      lambdaLifecycleCallbacksLog.warn("The IDE is gone, so $callbackName did not run for $contextName")
      return
    }
    error("The IDE session is not started. Cannot run $callbackName for $contextName")
  }
  try {
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking(testSuiteSupervisorScope.coroutineContext) {
      currentIde.invokeLifecycleCallback(callbackName) { rdTestSession, callbackContextName ->
        when (callback) {
          LambdaLifecycleCallback.BEFORE_ALL -> rdTestSession.beforeAll.startSuspending(callbackContextName)
          LambdaLifecycleCallback.BEFORE_EACH -> rdTestSession.beforeEach.startSuspending(callbackContextName)
          LambdaLifecycleCallback.AFTER_EACH -> rdTestSession.afterEach.startSuspending(callbackContextName)
          LambdaLifecycleCallback.AFTER_ALL -> rdTestSession.afterAll.startSuspending(callbackContextName)
        }
      }
    }
  }
  catch (e: Throwable) {
    if (e is RdFault && e.reasonAsText.contains(IdeaLogger::class.java.name)) {
      lambdaLifecycleCallbacksLog.warn("Got a logger error during $callbackName for $contextName", e)
      // this is just a logged error thrown as exception in case of platform test framework modules are enabled on IDE side, can be skipped
      // probably should be handled better by turning off this behavior on the test framework side
      return
    }
    val message = "$callbackName failed for $contextName"
    catchAll("Gathering thread dumps '$message'") {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking {
        val artifactFileName = getArtifactsFileName(callbackName, "ThreadDumps-$contextName", "log")
        listOfNotNull(currentIde.backgroundRun, currentIde.backendIdeWithLambda?.backgroundRun).forEach { backgroundRun ->
          backgroundRun.collectJavaThreadDump(artifactFileName)
        }
      }
    }
    if (!context.executionException.isPresent) {
      CIServer.instance.reportTestFailure(message,
                                          message + "\n" + e.printToString(),
                                          "",
                                          kind = TeamCityReporter.SyntheticTestKind.TEST_INFRA_EXCEPTION)
    }
    else {
      lambdaLifecycleCallbacksLog.warn(message, e)
    }

    LambdaIdeSession.stop(context)
    throw e
  }
}

private val lambdaLifecycleCallbacksLog = logger<LambdaLifecycleCallback>()

private suspend fun IdeWithLambda.invokeLifecycleCallback(
  callbackName: String,
  callback: suspend (LambdaRdTestSession, String) -> Unit,
) {
  listOfNotNull(this, backendIdeWithLambda).map { it.rdSession }.forEach { session ->
    val title = session.getRdIdeTypePrefix() + callbackName
    runLogged(title, getTimeoutHonouringDebug(30.seconds)) {
      callback(session, title)
    }
  }
}
