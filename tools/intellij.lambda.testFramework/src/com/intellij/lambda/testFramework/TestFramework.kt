package com.intellij.lambda.testFramework

import com.intellij.openapi.diagnostic.Logger
import com.intellij.remoteDev.tests.impl.RdctTestFrameworkLoggerCategory
import com.intellij.testFramework.TestLoggerFactory

val frameworkLogger: Logger by lazy {
  if (!Logger.isInitialized())
    Logger.setFactory(TestLoggerFactory::class.java)
  Logger.getInstance(RdctTestFrameworkLoggerCategory.category)
}

fun assertThatSoftly(errorMessage: String = "", assertion: () -> Boolean) {
  assertThatSoftly(errorMessage, assertion) { it }
}

// error will be reported as separate failure on TC and will have no impact on test status
fun <T> assertThatSoftly(errorMessage: String = "", subject: () -> T, assertion: (T) -> Boolean): T {
  val s = subject()
  if (!assertion(s)) {
   frameworkLogger.error("$errorMessage: Actual: $s")
  }
  return s
}