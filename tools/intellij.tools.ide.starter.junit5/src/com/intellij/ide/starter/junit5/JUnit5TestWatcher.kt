package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.TestWatcherActions
import com.intellij.tools.ide.util.common.logError
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher

class JUnit5TestWatcher(private val testContextGetter: () -> IDETestContext?) : TestWatcher {
  val watcherActions = TestWatcherActions()

  private fun callOnFinishedActions(ideTestContext: IDETestContext) =
    watcherActions.onFinishedActions.forEach { action -> action(ideTestContext) }

  private fun callOrLog(ideTestContext: IDETestContext?, method: (IDETestContext) -> Unit) {
    ideTestContext
      ?.let { method(it) }
    ?: logError(MISSING_CONTEXT_LOG_MESSAGE)
  }

  override fun testSuccessful(context: ExtensionContext?) {
    callOrLog(testContextGetter(), ::callOnFinishedActions)
    super.testSuccessful(context)
  }

  override fun testFailed(context: ExtensionContext?, cause: Throwable?) {
    callOrLog(testContextGetter()) {
      watcherActions.onFailureActions.forEach { action -> action(it) }
      callOnFinishedActions(it)
    }
    super.testFailed(context, cause)
  }
  companion object {
    internal const val MISSING_CONTEXT_LOG_MESSAGE: String = "JUnit5TestWatcher can't run as there is no context"
  }
}
