package com.intellij.turboComplete.analysis.fus

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupResultDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageTracker
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension

class MLPerformanceTracker : LookupUsageDescriptor {
  override fun getExtensionKey(): String = "ml_performance"

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    val lookup = lookupResultDescriptor.lookup
    val data = mutableListOf<EventPair<*>>()
    if (lookup.isCompletion && lookup is LookupImpl) {
      val storage = LookupStorage.get(lookup)
      if (storage != null) {
        val enabled = storage.contextFactors["ml_ctx_common_completion_kind_performance_enabled"] == "1"
        val shownEarly = storage.contextFactors["ml_ctx_common_completion_kind_show_lookup_early"] == "1"
        data.add(MLPerformanceUsageCollectorExtension.ML_PERFORMANCE_ENABLED.with(enabled))
        data.add(MLPerformanceUsageCollectorExtension.LOOKUP_SHOWN_EARLY.with(shownEarly))
      }
    }
    return data
  }

  class MLPerformanceUsageCollectorExtension : FeatureUsageCollectorExtension {
    override fun getGroupId(): String {
      return LookupUsageTracker.GROUP_ID
    }

    override fun getEventId(): String {
      return LookupUsageTracker.FINISHED_EVENT_ID
    }

    override fun getExtensionFields(): List<EventField<*>> {
      return listOf<EventField<*>>(ML_PERFORMANCE_ENABLED, LOOKUP_SHOWN_EARLY)
    }

    companion object {
      val ML_PERFORMANCE_ENABLED = EventFields.Boolean("ml_performance_enabled")
      val LOOKUP_SHOWN_EARLY = EventFields.Boolean("lookup_shown_early")
    }
  }
}
