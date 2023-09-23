package com.intellij.searchEverywhereMl

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName

abstract class SearchEverywhereSessionPropertyProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<SearchEverywhereSessionPropertyProvider> = ExtensionPointName.create(
      "com.intellij.searchEverywhereMl.searchEverywhereSessionPropertyProvider")

    fun getAllDeclarations(): List<EventField<*>> {
      return getPropertyProviders().flatMap { it.getDeclarations() }
    }

    fun getAllProperties(tabId: String): List<EventPair<*>> {
      return getPropertyProviders().flatMap { it.getProperties(tabId) }
    }

    private fun getPropertyProviders(): List<SearchEverywhereSessionPropertyProvider> {
      return EP_NAME.extensionList.filter { getPluginInfo(it.javaClass).isDevelopedByJetBrains() }
    }
  }

  abstract fun getDeclarations(): List<EventField<*>>

  abstract fun getProperties(tabId: String): List<EventPair<*>>
}