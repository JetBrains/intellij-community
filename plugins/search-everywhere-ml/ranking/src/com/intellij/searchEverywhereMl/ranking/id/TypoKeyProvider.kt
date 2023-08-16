package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult

private class TypoKeyProvider : ElementKeyForIdProvider {
  override fun getKey(element: Any): Any? {
    return element as? SearchEverywhereSpellCheckResult.Correction
  }
}
