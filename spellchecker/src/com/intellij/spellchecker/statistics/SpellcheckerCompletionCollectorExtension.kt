// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.statistics

import com.intellij.codeInsight.lookup.impl.LookupUsageTracker
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.spellchecker.statistics.SpellcheckerLookupUsageDescriptor.Companion.SPELLCHECKER

internal class SpellcheckerCompletionCollectorExtension: FeatureUsageCollectorExtension {
  override fun getGroupId(): String {
    return LookupUsageTracker.GROUP_ID
  }

  override fun getEventId(): String {
    return LookupUsageTracker.FINISHED_EVENT_ID
  }

  override fun getExtensionFields(): List<EventField<*>> {
    return listOf(spellchecker)
  }

  companion object {
    val spellchecker = EventFields.Boolean(SPELLCHECKER);
  }
}