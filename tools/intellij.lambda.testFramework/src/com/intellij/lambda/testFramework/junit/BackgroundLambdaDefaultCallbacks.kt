package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.coroutine.CommonScope.testSuiteSupervisorScope
import com.intellij.idea.IdeaLogger
import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.lambda.testFramework.starter.IdeInstance.ide
import com.intellij.lambda.testFramework.starter.IdeInstance.isStarted
import com.intellij.ide.starter.process.collectJavaThreadDumpSuspendable
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.catchAll
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.remoteDev.tests.impl.utils.getArtifactsFileName
import com.intellij.remoteDev.tests.impl.utils.getTimeoutHonouringDebug
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import com.jetbrains.rd.util.reactive.RdFault
import com.jetbrains.rd.util.string.printToString
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Ensures `BackgroundRunWithLambda.cleanUp()` is executed automatically after each test.
 *
 * - Tolerates absence of a started IDE (no-op).
 * - Logs failures but does not fail the test to avoid masking the original result.
 */
class BackgroundLambdaDefaultCallbacks : BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    callbackInvoker(context, "Before all") { rdTestSession, contextName -> rdTestSession.beforeAll.startSuspending(contextName) }
  }

  override fun beforeEach(context: ExtensionContext) {
    callbackInvoker(context, "Before each") { rdTestSession, contextName -> rdTestSession.beforeEach.startSuspending(contextName) }
  }

  override fun afterEach(context: ExtensionContext) {
    callbackInvoker(context, "After each") { rdTestSession, contextName -> rdTestSession.afterEach.startSuspending(contextName) }
  }

  override fun afterAll(context: ExtensionContext) {
    callbackInvoker(context, "After all") { rdTestSession, contextName -> rdTestSession.afterAll.startSuspending(contextName) }
  }


  private fun callbackInvoker(
    context: ExtensionContext,
    callbackName: String,
    callback: suspend (LambdaRdTestSession, String) -> Unit,
  ) {
    val contextName = buildString {
      append(context.requiredTestClass.name)
      context.testMethod.getOrNull()?.let { append(".${it.name} ") }
      append(context.displayName)
    }

    if (!isStarted()) {
      thisLogger().warn("IDE wasn't started yet.Skipping $callbackName for $contextName")
      return
    }

    try {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking(testSuiteSupervisorScope.coroutineContext) {
        ide.apply {
          listOfNotNull(this, backendIdeWithLambda).map { it.rdSession }.forEach { session ->
            val title = session.getRdIdeTypePrefix() + callbackName
            runLogged(title, getTimeoutHonouringDebug(30.seconds)) {
              callback(session, title)
            }
          }
        }
      }
    }
    catch (e: Throwable) {
      if (e is RdFault && e.reasonAsText.contains(IdeaLogger::class.java.name)) {
        thisLogger().warn("Got a logger error during $callbackName for $contextName", e)
        // this is just a logged error thrown as exception in case of platform test framework modules are enabled on IDE side, can be skipped
        // probably should be handled better by turning off this behavior on the test framework side
        return
      }
      val message = "$callbackName failed for $contextName"
      catchAll("Gathering thread dumps '$message'") {
        @Suppress("RAW_RUN_BLOCKING")
        runBlocking {

          suspend fun IdeWithLambda.dumpProcess(runContext: IDERunContext) {
            collectJavaThreadDumpSuspendable(runContext.testContext.ide.resolveAndDownloadTheSameJDKOrFallback(),
                                             runContext.lastIdeReportingData.logsDir,
                                             backgroundRun.process.id.toLong(),
                                             runContext.lastIdeReportingData.logsDir.resolve(
                                               getArtifactsFileName(callbackName, "ThreadDumps-$contextName", "log")))
          }
          ide.dumpProcess(IdeInstance.runContext.frontendContext)
          ide.backendIdeWithLambda?.let { backendIde ->
            IdeInstance.runContext.backendContext?.let { backendContext ->
              backendIde.dumpProcess(backendContext)
            } ?: {
              thisLogger().warn("Backend context is not yet initialized")
            }
          }
        }
      }
      if (!context.executionException.isPresent) {
        CIServer.instance.reportTestFailure(message, message + "\n" + e.printToString(), "", kind = TeamCityReporter.SyntheticTestKind.TEST_INFRA_EXCEPTION)
      }
      else {
        thisLogger().warn(message, e)
      }

      IdeInstance.stopIde()
      throw e
    }
  }
}
