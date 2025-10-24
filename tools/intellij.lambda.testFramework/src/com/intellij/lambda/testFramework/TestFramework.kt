package com.intellij.lambda.testFramework

import com.intellij.internal.DebugAttachDetector
import com.intellij.openapi.diagnostic.Logger
import com.intellij.remoteDev.tests.impl.RdctTestFrameworkLoggerCategory
import com.intellij.testFramework.TestLoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

val frameworkLogger: Logger by lazy {
  if (!Logger.isInitialized())
    Logger.setFactory(TestLoggerFactory::class.java)
  Logger.getInstance(RdctTestFrameworkLoggerCategory.category)
}

// Determines if long timeout is used for the local debug of the tests.
private const val defaultLongWaitingOnDebug = true
private val debugLongTimeout = 30.minutes
private val isDebugging by lazy { DebugAttachDetector.isAttached() }

fun getTimeoutHonouringDebug(providedTimeout: Duration): Duration =
  if (isDebugging && defaultLongWaitingOnDebug) {
    debugLongTimeout
  }
  else {
    providedTimeout
  }

fun getTimeoutHonouringDebug(providedTimeout: Duration?, defaultTimeout: Duration): Duration =
  getTimeoutHonouringDebug(providedTimeout ?: defaultTimeout)

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