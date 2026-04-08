// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.jetbrains.performancePlugin.commands.CodeAnalysisStateListener
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HighlightingTestUtil {

  companion object {

    @JvmStatic
    fun storeProcessFinishedTime(scopeName: String, spanName: String, vararg additionalAttributes: Pair<String, String>) {
      val span = TelemetryManager.getTracer(Scope(scopeName))
        .spanBuilder(spanName)
        .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        .startSpan()
        .setAttribute("finish", System.currentTimeMillis())
      additionalAttributes.forEach { attributesPair -> span.setAttribute(attributesPair.first, attributesPair.second) }
      span.end(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    }


    @JvmStatic
    suspend fun waitForAnalysisWithNewApproach(project: Project, spanRef: Ref<Span>, timeout: Long, suppressErrors: Boolean) {
      waitForAnalysisWithNewApproach(project, spanRef.get(), timeout, suppressErrors)
    }

    @JvmStatic
    suspend fun waitForAnalysisWithNewApproach(project: Project, span: Span?, timeoutSeconds: Long?, suppressErrors: Boolean?) {
      val timeoutDuration = if (timeoutSeconds == 0L || timeoutSeconds == null) 5.minutes else timeoutSeconds.seconds
      try {
        project.service<CodeAnalysisStateListener>().waitAnalysisToFinish(timeoutDuration, !(suppressErrors ?: true))
      }
      catch (e: TimeoutException) {
        span?.setAttribute("timeout", "true")
      }
      finally {
        span?.end()
      }
    }

    suspend fun waitForCondition(checkInterval: Long = 500, condition: suspend () -> Boolean) {
      while (true) {
        if (condition()) return
        delay(checkInterval)
      }
    }

    suspend fun <T> waitForCondition(
      checkInterval: Long = 500,
      timeout: Duration = 30_000.milliseconds,
      timeoutReason: String? = null,
      condition: suspend () -> T?,
    ): T {
      timeoutReason?.let { logger<HighlightingTestUtil>().info(it) }
      val result = withTimeoutOrNull(timeout) {
        while (true) {
          val value = condition()
          if (value != null) return@withTimeoutOrNull value
          delay(checkInterval)
        }
      }
      @Suppress("UNCHECKED_CAST")
      return result as? T ?: error("Timeout after $timeout${timeoutReason?.let { ": $it" } ?: ""}")
    }
  }

}