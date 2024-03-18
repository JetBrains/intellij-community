package com.intellij.searchEverywhereMl.ranking.ext

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
interface SearchEverywhereElementKeyProvider {
  companion object {
    val EP_NAME = ExtensionPointName.create<SearchEverywhereElementKeyProvider>("com.intellij.searchEverywhereMl.searchEverywhereElementKeyProvider")

    fun getKeyOrNull(element: Any): Any? = EP_NAME.extensionList
      .filter { getPluginInfo(it.javaClass).isDevelopedByJetBrains() }
      .firstNotNullOfOrNull { it.getKeyOrNull(element) }
  }

  /**
   * Returns a key used to identify the same element in Search Everywhere throughout different states
   */
  fun getKeyOrNull(element: Any): Any?
}