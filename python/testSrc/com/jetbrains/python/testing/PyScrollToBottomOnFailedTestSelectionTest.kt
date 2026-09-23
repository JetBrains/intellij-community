// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.testframework.sm.runner.MockRuntimeConfiguration
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyTestCase
import java.util.concurrent.TimeUnit

/**
 * The console prints the output of a selected test on the test executor thread, and the scroll task follows it there.
 * In the unit test mode, that print runs on the calling thread, so the test selects the test on a pooled thread.
 */
@Subsystems.TestRunner
@Layers.Functional
@TestFor(classes = [PyScrollToBottomOnFailedTestSelection::class], issues = ["PY-91931"])
class PyScrollToBottomOnFailedTestSelectionTest : PyTestCase() {

  fun `test the selection of a failed test off the EDT scrolls the console on the EDT`() {
    val properties = SMTRunnerConsoleProperties(MockRuntimeConfiguration(myFixture.project), "pytest",
                                                DefaultRunExecutor.getRunExecutorInstance())
    val console = SMTRunnerConsoleView(properties)
    try {
      console.initUI()
      val viewer = console.resultsViewer
      val failedTest = SMTestProxy("test_fails", false, null).apply {
        setStarted()
        setTestFailed("assert False", null, false)
      }
      val listener = PyScrollToBottomOnFailedTestSelection(console)

      ApplicationManager.getApplication().executeOnPooledThread {
        listener.onSelected(failedTest, viewer, viewer)
      }.get(1, TimeUnit.MINUTES)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    finally {
      Disposer.dispose(console)
    }
  }
}
