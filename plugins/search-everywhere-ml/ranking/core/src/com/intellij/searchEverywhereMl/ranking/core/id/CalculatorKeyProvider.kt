package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.ide.actions.searcheverywhere.EvaluationResult
import com.intellij.searchEverywhereMl.ranking.ext.SearchEverywhereElementKeyProvider

internal class CalculatorKeyProvider: SearchEverywhereElementKeyProvider {
  override fun getKeyOrNull(element: Any): Any? = element as? EvaluationResult
}
