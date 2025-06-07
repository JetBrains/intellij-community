package com.intellij.searchEverywhereMl.typos

import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.isTypoExperiment

internal val isTypoFixingEnabled: Boolean
  get() {
    return SearchEverywhereTab.Actions.isTypoExperiment && Registry.`is`("search.everywhere.ml.spell.checker.enabled")
  }
