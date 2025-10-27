package com.intellij.ide.starter.junit5

import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan
import java.util.concurrent.atomic.AtomicBoolean

open class StarterBusTestPlanListener : TestExecutionListener {
  companion object {
    val isServerRunning = AtomicBoolean(false)
  }

  override fun testPlanExecutionStarted(testPlan: TestPlan?) {
    try {
      EventsBus.startServerProcess()
      isServerRunning.set(true)
    }
    catch (_: Throwable) {
      // Skip exceptions here to avoid failing all tests
    }
    super.testPlanExecutionStarted(testPlan)
  }

  override fun testPlanExecutionFinished(testPlan: TestPlan?) {
    try {
      if (isServerRunning.get()) {
        EventsBus.endServerProcess()
      }
      super.testPlanExecutionFinished(testPlan)
    }
    catch (ignored: Throwable) {
    }
  }
}