// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration

@ApiStatus.Internal
object PyTypeEvaluationCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("python.type.evaluation", 2)
  private val DURATION_BASKET = EventFields.Int("duration_basket", "Duration of type resolution, grouped into 100ms time buckets")
  private val JB_TYPE_ENGINE_TIME = GROUP.registerEvent("jb.type.engine.time", DURATION_BASKET)
  private val HYBRID_TYPE_ENGINE_TIME = GROUP.registerEvent("hybrid.type.engine.time", DURATION_BASKET)

  private val Duration.basket: Int
    get() = inWholeMilliseconds.toInt() / 100

  fun logJBTypeEngineTime(duration: Duration): Unit = JB_TYPE_ENGINE_TIME.log(duration.basket)

  fun logHybridTypeEngineTime(duration: Duration): Unit = HYBRID_TYPE_ENGINE_TIME.log(duration.basket)
}
