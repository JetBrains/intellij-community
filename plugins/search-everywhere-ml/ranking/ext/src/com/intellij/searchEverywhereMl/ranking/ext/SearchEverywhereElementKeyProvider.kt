package com.intellij.searchEverywhereMl.ranking.ext

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereElementKeyProvider {
  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereElementKeyProvider> =
      ExtensionPointName.create("com.intellij.searchEverywhereMl.searchEverywhereElementKeyProvider")

    fun getKeyOrNull(element: Any): Any? = EP_NAME.extensionList
      .filter { getPluginInfo(it.javaClass).isDevelopedByJetBrains() }
      .firstNotNullOfOrNull { it.getKeyOrNull(element) }
  }

  /**
   * Returns a key used to identify the same element in Search Everywhere throughout different states.
   *
   * Providers are called without an outer read-action.
   * Implementations that access PSI trees must wrap only that access in a read action themselves.
   */
  fun getKeyOrNull(element: Any): Any?
}