// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import java.util.concurrent.TimeUnit
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.jetbrains.performancePlugin.commands.CodeAnalysisStateListener
import io.opentelemetry.api.trace.Span
import java.util.concurrent.TimeoutException
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
    suspend fun waitForAnalysisWithNewApproach(project: Project, span: Span?, timeout: Long, suppressErrors: Boolean) {
      val timeoutDuration = if (timeout == 0L) 5.minutes else timeout.seconds
      try {
        project.service<CodeAnalysisStateListener>().waitAnalysisToFinish(timeoutDuration, !suppressErrors)
      }
      catch (e: TimeoutException) {
        span?.setAttribute("timeout", "true")
      }
      finally {
        span?.end()
      }
    }
  }

}