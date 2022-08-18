package com.intellij.ide.starter.junit4

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.TestWatcherActions
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class JUnit4TestWatcher(private val testContextGetter: () -> IDETestContext) : TestWatcher() {
  val watcherActions = TestWatcherActions()

  override fun finished(description: Description) {
    val testContext = testContextGetter()
    watcherActions.onFinishedActions.forEach { action -> action(testContext) }
    super.finished(description)
  }

  override fun failed(e: Throwable?, description: Description?) {
    val testContext = testContextGetter()
    watcherActions.onFailureActions.forEach { action -> action(testContext) }
    super.failed(e, description)
  }
}