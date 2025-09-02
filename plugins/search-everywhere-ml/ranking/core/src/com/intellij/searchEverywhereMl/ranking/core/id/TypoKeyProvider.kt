@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

private class TypoKeyProvider : SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? {
    return element as? SearchEverywhereSpellCheckResult.Correction
  }
}
