// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.statistics

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.openapi.util.Key

class SpellcheckerLookupUsageDescriptor : LookupUsageDescriptor {
  companion object {
    private const val SPELLCHECKER = "spellchecker"
    val SPELLCHECKER_KEY = Key<Boolean>(SPELLCHECKER)
  }

  override fun getExtensionKey(): String = SPELLCHECKER

  override fun fillUsageData(lookup: Lookup, usageData: FeatureUsageData) {
    if (lookup is LookupImpl && lookup.getUserData(SPELLCHECKER_KEY) == true) {
      usageData.apply {
        addData(SPELLCHECKER, true)
      }
    }
  }
}