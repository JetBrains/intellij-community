// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.testframework.sm.runner.MockRuntimeConfiguration
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The console prints the output of a selected test on the test executor thread, and the scroll task follows it there.
 * In the unit test mode, that print runs on the calling thread, so the test selects the test on a pooled thread.
 */
@Subsystems.TestRunner
@Layers.Functional
@TestFor(classes = [PyScrollToBottomOnFailedTestSelection::class], issues = ["PY-91931"])
class PyScrollToBottomOnFailedTestSelectionTest : PyCodeInsightTestCase() {

  @Test
  fun `the selection of a failed test off the EDT scrolls the console on the EDT`() = runInEdtAndWait {
    val properties = SMTRunnerConsoleProperties(MockRuntimeConfiguration(myFixture.project), "pytest",
                                                DefaultRunExecutor.getRunExecutorInstance())
    val scrollOffsets = mutableListOf<Int>()
    val console = object : SMTRunnerConsoleView(properties) {
      override fun scrollTo(offset: Int) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        scrollOffsets.add(offset)
        super.scrollTo(offset)
      }
    }
    try {
      console.initUI()
      val viewer = console.resultsViewer
      val failedTest = SMTestProxy("test_fails", false, null).apply {
        setStarted()
        setTestFailed("assert False", null, false)
      }
      val listener = PyScrollToBottomOnFailedTestSelection(console)
      val output = "AssertionError: expected True\n"
      console.console.print(output, ConsoleViewContentType.ERROR_OUTPUT)

      ApplicationManager.getApplication().executeOnPooledThread {
        listener.onSelected(failedTest, viewer, viewer)
      }.get(1, TimeUnit.MINUTES)
      PlatformTestUtil.waitWithEventsDispatching("The console did not scroll to the failed test output", { scrollOffsets.isNotEmpty() }, 10)
      assertThat(scrollOffsets).containsExactly(output.length)
    }
    finally {
      Disposer.dispose(console)
    }
  }
}
