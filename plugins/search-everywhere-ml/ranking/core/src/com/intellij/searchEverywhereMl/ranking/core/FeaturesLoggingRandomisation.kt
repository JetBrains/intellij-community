// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.isLoggingEnabled
import java.util.*

internal class FeaturesLoggingRandomisation {
  private val thresholdsByTab: Map<SearchEverywhereTab.TabWithLogging, Double> = hashMapOf(
    SearchEverywhereTab.Actions to 1.0,
    SearchEverywhereTab.Files to 0.5,
    SearchEverywhereTab.Classes to 0.5,
    SearchEverywhereTab.Symbols to 1.0,
    SearchEverywhereTab.All to 0.5
  )

  private val seed: Double = Random().nextDouble()

  private fun isInTestMode(): Boolean = ApplicationManagerEx.isInIntegrationTest() || ApplicationManagerEx.getApplication().isUnitTestMode

  fun shouldLogFeatures(tab: SearchEverywhereTab): Boolean {
    if (!tab.isLoggingEnabled()) {
      return false
    }

    return isInTestMode() || (seed < (thresholdsByTab[tab] ?: 1.0))
  }
}