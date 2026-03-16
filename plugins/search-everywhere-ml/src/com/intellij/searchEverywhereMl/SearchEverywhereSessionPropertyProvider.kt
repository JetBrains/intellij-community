package com.intellij.searchEverywhereMl

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair


@Deprecated(message = "This class is deprecated and kept only for source compatibility with AI Assistant Semantic Search." +
                      "Use com.intellij.searchEverywhereMl.features.SearchEverywhereStateFeaturesProvider instead.",
            replaceWith = ReplaceWith("SearchEverywhereStateFeaturesProvider", "com.intellij.searchEverywhereMl.features.SearchEverywhereStateFeaturesProvider"))
abstract class SearchEverywhereSessionPropertyProvider {
  abstract fun getDeclarations(): List<EventField<*>>

  abstract fun getProperties(tabId: String): List<EventPair<*>>
}