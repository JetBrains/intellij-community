package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult

private class TypoKeyProvider : ElementKeyForIdProvider {
  override fun getKey(element: Any): Any? {
    return element as? SearchEverywhereSpellCheckResult.Correction
  }
}
