// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.MathUtil
import org.HdrHistogram.Recorder
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * FUS aggregates collector for Python type evaluation statistics.
 * Collects percentile-based aggregated statistics to reduce FUS load.
 * Statistics are automatically flushed periodically by the FUS framework.
 */
@ApiStatus.Internal
class PyTypeEvaluationAggregatesCollector : ApplicationUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val metrics = mutableSetOf<MetricEvent>()

    for ((engineName, recorder) in recorders) {
      val histogram = recorder.intervalHistogram
      if (histogram.totalCount > 0) {
        metrics.add(
          EVENT_TYPE_ENGINE_EVALUATION.metric(
            TYPE_ENGINE_FIELD.with(engineName),
            EVALUATIONS_TOTAL_FIELD.with(histogram.totalCount.toInt()),
            DURATION_MEAN_FIELD.with(histogram.mean),
            DURATION_90P_FIELD.with(histogram.getValueAtPercentile(90.0).toInt()),
            DURATION_95P_FIELD.with(histogram.getValueAtPercentile(95.0).toInt()),
            DURATION_99P_FIELD.with(histogram.getValueAtPercentile(99.0).toInt()),
            DURATION_MAX_FIELD.with(histogram.maxValue.toInt())
          )
        )
      }
    }

    return metrics
  }

  companion object {
    private val LOG = logger<PyTypeEvaluationAggregatesCollector>()
    private val GROUP = EventLogGroup("python.type.evaluation.aggregates", 1)

    private const val MAX_TRACKABLE_DURATION_MS = 10000

    private const val PYCHARM_TYPE_ENGINE = "pycharm"
    private val EXTERNAL_TYPE_ENGINES = listOf("pyrefly", "ty")
    private val TYPE_ENGINE_FIELD = EventFields.String("type_engine", listOf(PYCHARM_TYPE_ENGINE) + EXTERNAL_TYPE_ENGINES)
    private val EVALUATIONS_TOTAL_FIELD = EventFields.Int("evaluations_total")
    private val DURATION_MEAN_FIELD = EventFields.Double("duration_mean_ms")
    private val DURATION_90P_FIELD = EventFields.Int("duration_90ile_ms")
    private val DURATION_95P_FIELD = EventFields.Int("duration_95ile_ms")
    private val DURATION_99P_FIELD = EventFields.Int("duration_99ile_ms")
    private val DURATION_MAX_FIELD = EventFields.Int("duration_max_ms")

    private val EVENT_TYPE_ENGINE_EVALUATION = GROUP.registerVarargEvent(
      "type.engine.evaluation",
      TYPE_ENGINE_FIELD,
      EVALUATIONS_TOTAL_FIELD,
      DURATION_MEAN_FIELD,
      DURATION_90P_FIELD,
      DURATION_95P_FIELD,
      DURATION_99P_FIELD,
      DURATION_MAX_FIELD
    )

    private val recorders = ConcurrentHashMap<String, Recorder>()

    private fun getOrCreateRecorder(typeEngineName: String): Recorder {
      return recorders.computeIfAbsent(typeEngineName) {
        Recorder(MAX_TRACKABLE_DURATION_MS.toLong(), 2)
      }
    }

    fun recordPyCharmTypeEngineTime(durationMs: Long) {
      val clampedDurationMs = MathUtil.clamp(durationMs, 0, MAX_TRACKABLE_DURATION_MS.toLong())
      getOrCreateRecorder(PYCHARM_TYPE_ENGINE).recordValue(clampedDurationMs)
    }

    fun recordHybridTypeEngineTime(typeEngineName: String, durationMs: Long) {
      val recorderName = typeEngineName.lowercase()
      // Only record metrics for registered external type engines
      if (recorderName !in EXTERNAL_TYPE_ENGINES) {
        LOG.error("Unknown type engine name '${typeEngineName}' (normalized: '${recorderName}'). Expected one of: ${EXTERNAL_TYPE_ENGINES.joinToString()}")
        return
      }
      val clampedDurationMs = MathUtil.clamp(durationMs, 0, MAX_TRACKABLE_DURATION_MS.toLong())
      getOrCreateRecorder(recorderName).recordValue(clampedDurationMs)
    }
  }
}
