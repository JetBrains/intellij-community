package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.coroutine.CommonScope.testSuiteSupervisorScope
import com.intellij.idea.IdeaLogger
import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.lambda.testFramework.starter.IdeInstance.ide
import com.intellij.lambda.testFramework.starter.IdeInstance.isStarted
import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.rd.util.reactive.RdFault
import com.jetbrains.rd.util.string.printToString
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.jvm.optionals.getOrNull

/**
 * Ensures `BackgroundRunWithLambda.cleanUp()` is executed automatically after each test.
 *
 * - Tolerates absence of a started IDE (no-op).
 * - Logs failures but does not fail the test to avoid masking the original result.
 */
class BackgroundLambdaDefaultCallbacks : BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    callbackInvoker(context, "Before all",
                    { contextName, callbackName -> ide.forEachSession(callbackName) { it.beforeAll.startSuspending(contextName) } })
  }

  override fun beforeEach(context: ExtensionContext) {
    callbackInvoker(context, "Before each",
                    { contextName, callbackName -> ide.forEachSession(callbackName) { it.beforeEach.startSuspending(contextName) } })
  }

  override fun afterEach(context: ExtensionContext) {
    callbackInvoker(context, "After each",
                    { contextName, callbackName -> ide.forEachSession(callbackName) { it.afterEach.startSuspending(contextName) } })
  }

  override fun afterAll(context: ExtensionContext) {
    callbackInvoker(context, "After all",
                    { contextName, callbackName -> ide.forEachSession(callbackName) { it.afterAll.startSuspending(contextName) } })
  }


  private fun callbackInvoker(
    context: ExtensionContext,
    callbackName: String,
    callback: suspend (String, String) -> Unit,
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
        callback(contextName, callbackName)
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
      if (!context.executionException.isPresent) {
        CIServer.instance.reportTestFailure(message, message + "\n" + e.printToString(), "")
      }
      else {
        thisLogger().warn(message, e)
      }

      IdeInstance.stopIde()
      IdeInstance.publishArtifacts()
      throw e
    }
  }
}
