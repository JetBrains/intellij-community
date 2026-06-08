// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.statistics

import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

internal object StreamDebuggerStatisticsCollector : CounterUsagesCollector() {
  private enum class StreamTraceResult {
    SUCCESS,
    CLIENT_EXCEPTION,
    COMPILATION_FAILED,
    INTERNAL_ERROR,
  }

  private val GROUP = EventLogGroup("debugger.streams", 1)

  // We want to record type of the stream (stream api, streamex, kotlin sequence)
  // The type of the stream can be inferred from the library support provider class
  private val LIBRARY_SUPPORT_PROVIDER = EventFields.Class("librarySupportProvider")
  // We want to record type of the tracer (evaluate expression, breakpoint-based engine, smth new implemented in other plugins)
  private val TRACER = EventFields.Class("tracer")
  private val RESULT = EventFields.Enum("result", StreamTraceResult::class.java)
  private val TRACE_FINISHED = GROUP.registerVarargEvent("stream.trace.finished", LIBRARY_SUPPORT_PROVIDER, TRACER, RESULT)

  override fun getGroup(): EventLogGroup = GROUP

  @JvmStatic
  fun logTraceFinished(
    project: Project,
    librarySupportProvider: LibrarySupportProvider,
    tracer: StreamTracer,
    tracingResult: StreamTracer.Result,
  ) {
    val result = getTraceResult(tracingResult)
    val events: List<EventPair<*>> = listOf(
      LIBRARY_SUPPORT_PROVIDER.with(librarySupportProvider.javaClass),
      TRACER.with(tracer.javaClass),
      RESULT.with(result),
    )
    TRACE_FINISHED.log(project, events)
  }

  private fun getTraceResult(result: StreamTracer.Result): StreamTraceResult {
    return when (result) {
      is StreamTracer.Result.Evaluated -> {
        if (result.result.exceptionThrown()) StreamTraceResult.CLIENT_EXCEPTION else StreamTraceResult.SUCCESS
      }
      is StreamTracer.Result.EvaluationFailed -> StreamTraceResult.INTERNAL_ERROR
      is StreamTracer.Result.CompilationFailed -> StreamTraceResult.COMPILATION_FAILED
      StreamTracer.Result.Unknown -> StreamTraceResult.INTERNAL_ERROR
    }
  }
}
