package com.intellij.searchEverywhereMl.typos

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment.ExperimentType.ENABLE_TYPOS
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking

internal val isTypoFixingEnabled: Boolean
  get() {
    return (AdvancedSettings.getBoolean("searcheverywhere.ml.typos.enable")
            && SearchEverywhereMlExperiment().getExperimentForTab(SearchEverywhereTabWithMlRanking.ACTION) == ENABLE_TYPOS)
  }
