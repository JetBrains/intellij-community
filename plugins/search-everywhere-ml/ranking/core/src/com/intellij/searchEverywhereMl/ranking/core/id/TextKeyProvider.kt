package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

internal class TextKeyProvider : SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? = element as? SearchEverywhereItem
}
