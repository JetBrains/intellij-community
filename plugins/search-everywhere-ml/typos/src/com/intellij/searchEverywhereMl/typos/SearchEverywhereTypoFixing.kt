package com.intellij.searchEverywhereMl.typos

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.searchEverywhereMl.common.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.common.SearchEverywhereMlExperiment.ExperimentType.ENABLE_TYPOS
import com.intellij.searchEverywhereMl.common.SearchEverywhereTabWithMlRanking


internal val isTypoFixingEnabled: Boolean
  get() = AdvancedSettings.getBoolean("searcheverywhere.ml.typos.enable")
          && SearchEverywhereMlExperiment().getExperimentForTab(SearchEverywhereTabWithMlRanking.ACTION) == ENABLE_TYPOS
