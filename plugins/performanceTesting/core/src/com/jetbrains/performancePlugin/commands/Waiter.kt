// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.time.withTimeoutOrNull
import java.time.Duration
import java.time.temporal.TemporalUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

object Waiter {
  private const val DELAY = 100L

  @JvmStatic
  fun checkCondition(function: BooleanSupplier): CountDownLatch {
    val latch = CountDownLatch(1)
    val executor: ScheduledExecutorService = ConcurrencyUtil.newSingleScheduledThreadExecutor("Performance plugin waiter")
    executor.scheduleWithFixedDelay(
      {
        if (function.asBoolean) {
          latch.countDown()
          executor.shutdown()
        }
      }, 0, DELAY, TimeUnit.MILLISECONDS)
    return latch
  }

  /**
   * Waits for the specified time or throws exception
   * @timeout can be any positive long, if negative waiting will be skipped
   * @waitLogic lambda which contain yours business logic
   * @return calculated value from waitLogic or null if negative timeout was passed
   * @throws RuntimeException if condition of @waitLogic didn't return any value
   */
  @JvmStatic
  suspend fun <T> waitOrThrow(
    timeout: Long,
    timeunit: TemporalUnit,
    errorText: String? = null,
    waitLogic: suspend CoroutineScope.() -> T
  ): T? {
    return if (timeout > 0) {
      withTimeoutOrNull(Duration.of(timeout, timeunit), waitLogic)
      ?: throw RuntimeException(errorText ?: "Condition wasn't satisfied in $timeout $timeunit")
    }
    else {
      null
    }
  }

}
