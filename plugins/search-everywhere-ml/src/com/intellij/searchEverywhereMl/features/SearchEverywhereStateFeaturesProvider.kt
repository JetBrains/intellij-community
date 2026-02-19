package com.intellij.searchEverywhereMl.features

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.searchEverywhereMl.SearchEverywhereState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
/**
 * To implement additional features that are related to the current search state,
 * implement this interface and register the extension to the extension point.
 *
 * Most of the features will be reported by the plugin's
 * own [com.intellij.searchEverywhereMl.ranking.core.features.CoreStateFeaturesProvider].
 * This extension point is made so that search-state related features can be reported from outside of the plugin.
 */
interface SearchEverywhereStateFeaturesProvider {
  companion object {
    private val EP_NAME = ExtensionPointName.create<SearchEverywhereStateFeaturesProvider>("com.intellij.searcheverywhere.ml.searchEverywhereStateFeaturesProvider")

    private fun getProviders(): List<SearchEverywhereStateFeaturesProvider> {
      return EP_NAME.extensionList
        .filter { getPluginInfo(it.javaClass).isDevelopedByJetBrains() }
    }

    fun getFields(): List<EventField<*>> = getProviders().flatMap { it.fields }

    fun getFeatures(searchState: SearchEverywhereState): List<EventPair<*>> = getProviders().flatMap { it.getFeatures(searchState) }
  }

  /**
   * Declaration of fields that will be reported in the searchRestarted event
   */
  val fields: List<EventField<*>>

  /**
   * Retrieves features calculated based on the current search state
   */
  fun getFeatures(searchState: SearchEverywhereState): List<EventPair<*>>
}
