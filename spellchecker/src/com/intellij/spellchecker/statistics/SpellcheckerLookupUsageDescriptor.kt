// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.statistics

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupResultDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.util.Key
import com.intellij.spellchecker.statistics.SpellcheckerCompletionCollectorExtension.Companion.spellchecker
import java.util.*

class SpellcheckerLookupUsageDescriptor : LookupUsageDescriptor {
  companion object {
    internal const val SPELLCHECKER = "spellchecker"
    val SPELLCHECKER_KEY = Key<Boolean>(SPELLCHECKER)
  }

  override fun getExtensionKey(): String = SPELLCHECKER

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    val lookup = lookupResultDescriptor.lookup
    if (lookup is LookupImpl && lookup.getUserData(SPELLCHECKER_KEY) == true) {
      return Collections.singletonList(spellchecker.with(true))
    }
    return Collections.emptyList()
  }
}