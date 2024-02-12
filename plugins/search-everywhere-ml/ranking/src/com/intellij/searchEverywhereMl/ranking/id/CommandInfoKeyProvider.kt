package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereCommandInfo

internal class CommandInfoKeyProvider : ElementKeyForIdProvider {
  override fun getKey(element: Any): Any? {
    return when (element) {
      is SearchEverywhereCommandInfo -> element.command
      else -> null
    }
  }
}