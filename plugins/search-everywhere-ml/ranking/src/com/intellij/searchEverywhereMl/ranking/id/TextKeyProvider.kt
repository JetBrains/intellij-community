package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.find.impl.SearchEverywhereItem

internal class TextKeyProvider : ElementKeyForIdProvider {
  override fun getKey(element: Any): Any? = element as? SearchEverywhereItem
}
