@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.ide.actions.searcheverywhere.EvaluationResult
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

private class CalculatorKeyProvider: SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? = element as? EvaluationResult
}
