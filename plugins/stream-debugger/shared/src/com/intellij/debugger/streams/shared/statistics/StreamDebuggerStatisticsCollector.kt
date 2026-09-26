// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.shared.statistics

import com.intellij.debugger.streams.shared.TraceEntryPoint
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Lives in the shared module because both the backend (tracing itself) and the frontend (the inlay hint) report events.
 */
@ApiStatus.Internal
object StreamDebuggerStatisticsCollector : CounterUsagesCollector() {
  enum class StreamTraceResult {
    SUCCESS,
    CLIENT_EXCEPTION,
    COMPILATION_FAILED,
    INTERNAL_ERROR,
  }

  private val GROUP = EventLogGroup("debugger.streams", 3)

  // We want to record type of the stream (stream api, streamex, kotlin sequence)
  // The type of the stream can be inferred from the library support provider class
  private val LIBRARY_SUPPORT_PROVIDER = EventFields.Class("librarySupportProvider")
  // We want to record type of the tracer (evaluate expression, breakpoint-based engine, smth new implemented in other plugins)
  private val TRACER = EventFields.Class("tracer")
  private val RESULT = EventFields.Enum("result", StreamTraceResult::class.java)
  private val TRACE_FINISHED = GROUP.registerVarargEvent("stream.trace.finished", LIBRARY_SUPPORT_PROVIDER, TRACER, RESULT)

  // Records which entry point requested the trace: the toolbar action or the editor inlay hint.
  private val ENTRY_POINT = EventFields.Enum("entry_point", TraceEntryPoint::class.java)
  private val TRACE_STARTED = GROUP.registerEvent("stream.trace.started", ENTRY_POINT)

  private val INLAY_SETTING_CHANGED = GROUP.registerEvent("inlay.setting.changed", EventFields.Enabled)

  // The inlay is created and disposed on every pause, so the counters are aggregated and reported once per debug session.
  // `chain_found_count` counts the pause points where the hint could have appeared, regardless of the setting:
  // when the hint is turned off, `shown_count` stays zero while `chain_found_count` keeps growing.
  private val CHAIN_FOUND_COUNT = EventFields.Int("chain_found_count")
  private val SHOWN_COUNT = EventFields.Int("shown_count")
  private val CLICKED_COUNT = EventFields.Int("clicked_count")
  private val INLAY_SESSION_FINISHED = GROUP.registerVarargEvent("inlay.session.finished",
                                                                 CHAIN_FOUND_COUNT, SHOWN_COUNT, CLICKED_COUNT, EventFields.Enabled)

  override fun getGroup(): EventLogGroup = GROUP

  @JvmStatic
  fun logTraceFinished(
    project: Project,
    librarySupportProvider: Class<*>,
    tracer: Class<*>,
    result: StreamTraceResult,
  ) {
    val events: List<EventPair<*>> = listOf(
      LIBRARY_SUPPORT_PROVIDER.with(librarySupportProvider),
      TRACER.with(tracer),
      RESULT.with(result),
    )
    TRACE_FINISHED.log(project, events)
  }

  @JvmStatic
  fun logTraceStarted(project: Project, entryPoint: TraceEntryPoint) {
    TRACE_STARTED.log(project, entryPoint)
  }

  fun logInlaySettingChanged(enabled: Boolean) {
    INLAY_SETTING_CHANGED.log(enabled)
  }

  fun logInlaySessionFinished(project: Project, chainFoundCount: Int, shownCount: Int, clickedCount: Int, enabled: Boolean) {
    INLAY_SESSION_FINISHED.log(
      project,
      CHAIN_FOUND_COUNT.with(chainFoundCount),
      SHOWN_COUNT.with(shownCount),
      CLICKED_COUNT.with(clickedCount),
      EventFields.Enabled.with(enabled),
    )
  }
}
