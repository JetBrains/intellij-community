package com.intellij.ide.starter.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration

suspend fun <T> withRetryAsync(retries: Long = 3, messageOnFailure: String = "", retryAction: suspend () -> T): T =
  flow {
    emit(retryAction())
  }.retry(
    retries = retries,
    predicate = {
      it.printStackTrace()

      if (messageOnFailure.isNotBlank())
        logError(messageOnFailure)

      logError("Retrying in 10 sec ...")
      delay(Duration.seconds(10))
      true
    }
  ).last()

fun <T> withRetry(retries: Long = 3, messageOnFailure: String = "", retryAction: () -> T): T = runBlocking {
  withRetryAsync(retries, messageOnFailure) { retryAction() }
}
