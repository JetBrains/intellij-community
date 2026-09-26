package com.intellij.python.ty

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.types.PyType
import kotlin.time.measureTimedValue

object TyUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("python.ty.type.inference", 4)
  private val DURATION = EventFields.Int("duration_basket", "Duration of string type resolution, grouped into 100ms time buckets")
  private val STRING_TYPE_RESOLUTION_TIME = GROUP.registerEvent("string.type.resolution.time", DURATION)

  fun logStringTypeResolutionTime(block: () -> Ref<PyType?>?): Ref<PyType?>? {
    val (result, duration) = measureTimedValue {
      block()
    }
    val basket = (duration.inWholeMilliseconds / 100).toInt()
    STRING_TYPE_RESOLUTION_TIME.log(basket)
    return result
  }

  override fun getGroup(): EventLogGroup = GROUP
}
