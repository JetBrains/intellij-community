// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyTypeEvaluationStatisticsServiceImpl : PyTypeEvaluationStatisticsService {
  override fun logJBTypeEngineTime(durationMs: Long) {
    PyTypeEvaluationCollector.logJBTypeEngineTime(durationMs)
  }

  override fun logHybridTypeEngineTime(durationMs: Long) {
    PyTypeEvaluationCollector.logHybridTypeEngineTime(durationMs)
  }
}

/**
 * FUS collector for Python type evaluation statistics.
 *
 * This collector is registered separately from [PyTypeEvaluationStatisticsServiceImpl]
 * to avoid dual registration as both a service and an extension.
 */
@ApiStatus.Internal
object PyTypeEvaluationCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("python.type.evaluation", 2)
  private val DURATION_BASKET = EventFields.Int("duration_basket", "Duration of type resolution, grouped into 100ms time buckets")
  private val JB_TYPE_ENGINE_TIME = GROUP.registerEvent("jb.type.engine.time", DURATION_BASKET)
  private val HYBRID_TYPE_ENGINE_TIME = GROUP.registerEvent("hybrid.type.engine.time", DURATION_BASKET)

  private fun getDurationBasket(durationMs: Long): Int {
    return (durationMs / 100).toInt()
  }

  fun logJBTypeEngineTime(durationMs: Long) {
    JB_TYPE_ENGINE_TIME.log(getDurationBasket(durationMs))
  }

  fun logHybridTypeEngineTime(durationMs: Long) {
    HYBRID_TYPE_ENGINE_TIME.log(getDurationBasket(durationMs))
  }
}
