// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking
import java.util.*

internal class FeaturesLoggingRandomisation {
  private val thresholdsByTab = hashMapOf(
    SearchEverywhereTabWithMlRanking.ACTION.tabId to 1.0,
    SearchEverywhereTabWithMlRanking.FILES.tabId to 0.5,
    ClassSearchEverywhereContributor::class.java.simpleName to 0.5,
    SymbolSearchEverywhereContributor::class.java.simpleName to 1.0,
    SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID to 0.5
  )

  private val seed: Double = Random().nextDouble()

  private fun isInTestMode(): Boolean = ApplicationManagerEx.isInIntegrationTest() || ApplicationManagerEx.getApplication().isUnitTestMode

  fun shouldLogFeatures(tabId: String): Boolean = isInTestMode() || (seed < (thresholdsByTab[tabId] ?: 1.0))
}