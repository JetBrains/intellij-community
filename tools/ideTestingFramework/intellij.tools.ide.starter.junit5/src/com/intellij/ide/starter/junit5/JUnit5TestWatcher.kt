package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.TestWatcherActions
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher

class JUnit5TestWatcher(private val testContextGetter: () -> IDETestContext) : TestWatcher {
  val watcherActions = TestWatcherActions()

  override fun testSuccessful(context: ExtensionContext?) {
    watcherActions.onFinishedActions.forEach { action -> action(testContextGetter()) }
    super.testSuccessful(context)
  }

  override fun testFailed(context: ExtensionContext?, cause: Throwable?) {
    watcherActions.onFailureActions.forEach { action -> action(testContextGetter()) }
    super.testFailed(context, cause)
  }
}