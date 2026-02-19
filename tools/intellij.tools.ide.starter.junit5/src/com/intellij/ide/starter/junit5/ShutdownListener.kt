package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.report.AllureReport
import com.intellij.tools.ide.util.common.logOutput
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext

class ShutdownListener : ExecutionCondition {

  companion object {
    var shuttingDown: Boolean = false
  }

  init {
    Runtime.getRuntime().addShutdownHook(Thread(Runnable {
      shuttingDown = true
      logOutput("ShutdownListener registered shutting down, no more tests should start after this point")
    }, "Shutdown-indicator"))
  }

  override fun evaluateExecutionCondition(context: ExtensionContext?): ConditionEvaluationResult? {
    return if (shuttingDown) {
      val message = "Execution of '${context?.displayName}' cannot begin because shutdown is in progress, see AT-2253"
      logOutput(message)
      AllureReport.reportFailure("BuildExecution",
                                 "Test execution cannot begin because shutdown is in progress, probably Build timeout see AT-2253",
                                 suffix = "BuildError",
                                 originalStackTrace = "")
      ConditionEvaluationResult.disabled(message)
    }
    else {
      ConditionEvaluationResult.enabled("Test execution can begin")
    }
  }
}