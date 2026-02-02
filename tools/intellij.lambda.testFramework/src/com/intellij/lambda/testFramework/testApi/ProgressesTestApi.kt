package com.intellij.lambda.testFramework.testApi

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProgressModel
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForProgressesToFinish(timeout: Duration, predicate: (ProgressModel) -> Boolean = { true }) {
  waitSuspending("Waiting till all core progress managers are done", timeout) {
    activeProgresses().none { predicate(it) }
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForProgressesToAppear(timeout: Duration = 15.seconds, predicate: (ProgressModel) -> Boolean = { true }) =
  waitSuspending("Waiting till core progress managers appears", timeout,
                                                                          getter = { activeProgresses().filter { predicate(it) } },
                                                                          checker = { it.isNotEmpty() }
  )

context(lambdaIdeContext: LambdaIdeContext)
fun activeProgresses(): List<ProgressModel> {
  val statusBar = WindowManager.getInstance().getStatusBar(getProject()) as? StatusBarEx ?: run {
    return emptyList()
  }
  return statusBar.backgroundProcessModels.map { it.second }
}

context(lambdaIdeContext: LambdaIdeContext)
fun hasProgresses(predicate: (ProgressModel) -> Boolean = { true }): Boolean {
  val progresses = activeProgresses()
  frameworkLogger.info("Progresses exist: ${progresses.joinToString(", ") { it.getText() ?: "" }}")
  return progresses.any(predicate)
}

context(lambdaIdeContext: LambdaIdeContext)
fun hasProgressWithText(substring: String): Boolean {
  val progresses = activeProgresses()
  val hasProgress = progresses.any { it.getText()?.contains(substring, ignoreCase = true) == true }
  return hasProgress
}

// TODO: what is the hasUnsafeProgressIndicator check? can be replaced with hasProgresses?
context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForNoUnsafeProgressIndicatorsToFinish(timeout: Duration = 90.seconds) {
  waitSuspending("No progress indicators", timeout, failMessageProducer = {
    "Current indicators: ${
      CoreProgressManager.getCurrentIndicators().joinToString(", ") { it.text }
    }"
  }) {
    ProgressManager.getInstance().hasUnsafeProgressIndicator().not()
  }
}