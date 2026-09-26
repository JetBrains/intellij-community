package com.intellij.lambda.testFramework.testApi.utils

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.remoteDev.tests.impl.utils.runLogged
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun <P> tryTimes(
  attempts: Int,
  description: String,
  delay: Duration = 1.seconds,
  dynamicallyIncreaseDelay: Boolean = false,
  onAttemptFail: suspend (Throwable) -> Unit = {},
  onFinalFail: suspend (Throwable) -> Unit = {},
  failureMessage: String? = null,
  action: suspend (Int) -> P,
): P {

  var finalError: Throwable? = null
  var toDelay = delay
  for (i in 1..attempts) {
    try {
      return runLogged("$description: Attempt #$i(from $attempts)") {
        action(i)
      }
    }
    catch (e: Throwable) {
      onAttemptFail(e)
      finalError = e
      delay(toDelay)
      if (dynamicallyIncreaseDelay) {
        toDelay *= 2
      }
    }
  }
  frameworkLogger.info("$description: Failed $attempts attempts: " + failureMessage?.let { "$it: " } + finalError!!)
  onFinalFail(finalError)
  throw finalError
}

fun <P> tryTimesBlocking(
  attempts: Int,
  description: String,
  delay: Duration = 1.seconds,
  dynamicallyIncreaseDelay: Boolean = false,
  onAttemptFail: suspend (Throwable) -> Unit = {},
  onFinalFail: (Throwable) -> Unit = {},
  failureMessage: String? = null,
  action: suspend (Int) -> P,
): P =
  runBlocking {
    tryTimes(attempts, description, delay, dynamicallyIncreaseDelay, onAttemptFail, onFinalFail, failureMessage, action)
  }
