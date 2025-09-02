@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereCommandInfo
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

internal class CommandInfoKeyProvider : SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? {
    return when (element) {
      is SearchEverywhereCommandInfo -> element.command
      else -> null
    }
  }
}