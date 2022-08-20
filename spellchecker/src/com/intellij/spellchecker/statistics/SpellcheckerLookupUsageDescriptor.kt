// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.statistics

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupResultDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageTracker
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.openapi.util.Key
import com.intellij.spellchecker.statistics.SpellcheckerLookupUsageDescriptor.SpellcheckerCompletionCollectorExtension.Companion.SPELLCHECKER
import java.util.*

class SpellcheckerLookupUsageDescriptor : LookupUsageDescriptor {
  companion object {
    internal const val SPELLCHECKER_KEY_NAME = "spellchecker"
    val SPELLCHECKER_KEY = Key<Boolean>(SPELLCHECKER_KEY_NAME)
  }

  override fun getExtensionKey(): String = SPELLCHECKER_KEY_NAME

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    val lookup = lookupResultDescriptor.lookup
    if (lookup is LookupImpl && lookup.getUserData(SPELLCHECKER_KEY) == true) {
      return Collections.singletonList(SPELLCHECKER.with(true))
    }
    return Collections.emptyList()
  }

  internal class SpellcheckerCompletionCollectorExtension: FeatureUsageCollectorExtension {
    override fun getGroupId(): String {
      return LookupUsageTracker.GROUP_ID
    }

    override fun getEventId(): String {
      return LookupUsageTracker.FINISHED_EVENT_ID
    }

    override fun getExtensionFields(): List<EventField<*>> {
      return listOf(SPELLCHECKER)
    }

    companion object {
      val SPELLCHECKER = EventFields.Boolean(SPELLCHECKER_KEY_NAME)
    }
  }
}