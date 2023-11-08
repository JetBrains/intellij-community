package com.intellij.tools.ide.util.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class PrintFailuresMode {
  /** Print exceptions from each retry */
  ALL_FAILURES,

  /** Print only the very last exception from the latest retry */
  ONLY_LAST_FAILURE;
}


/** @return T - if successful; null - otherwise */
suspend fun <T> withRetryAsync(messageOnFailure: String,
                               retries: Long = 3,
                               printFailuresMode: PrintFailuresMode = PrintFailuresMode.ALL_FAILURES,
                               delay: Duration = 10.seconds,
                               retryAction: suspend () -> T): T? {

  (1..retries).forEach { failureCount ->
    try {
      return retryAction()
    }
    catch (e: NoRetryException) {
      throw e
    }
    catch (t: Throwable) {
      if (messageOnFailure.isNotBlank()) logError(messageOnFailure)

      when (printFailuresMode) {
        PrintFailuresMode.ALL_FAILURES -> t.printStackTrace()
        PrintFailuresMode.ONLY_LAST_FAILURE -> if (failureCount == retries) {
          logError("Last failure:")
          t.printStackTrace()
        }
      }

      if (failureCount < retries) {
        logError("Retrying in ${delay} ...")
        delay(delay)
      }
    }
  }

  return null
}

/**
 * Do not retry if code fails with this exception or its inheritors
 */
open class NoRetryException(message: String, cause: Throwable?) : IllegalStateException(message, cause)

/** @return T - if successful; null - otherwise */
@Suppress("RAW_RUN_BLOCKING")
fun <T> withRetry(
  messageOnFailure: String,
  retries: Long = 3,
  printFailuresMode: PrintFailuresMode = PrintFailuresMode.ALL_FAILURES,
  delay: Duration = 10.seconds,
  retryAction: () -> T
): T? = runBlocking(Dispatchers.IO) {
  withRetryAsync(
    messageOnFailure = messageOnFailure,
    retries = retries,
    printFailuresMode = printFailuresMode,
    delay = delay
  ) { retryAction() }
}

fun <T> executeWithRetry(retries: Int = 3, exception: Class<*>,
                         errorMsg: String = "Fail to execute action $retries attempts",
                         delay: Duration,
                         call: () -> T): T {
  for (i in 0..retries) {
    try {
      return call()
    }
    catch (e: Exception) {
      logError("Got error $e on $i attempt")
      if (e::class.java == exception) {
        Thread.sleep(delay.inWholeMilliseconds)
      }
      else throw e
    }
  }
  throw IllegalStateException(errorMsg)
}
