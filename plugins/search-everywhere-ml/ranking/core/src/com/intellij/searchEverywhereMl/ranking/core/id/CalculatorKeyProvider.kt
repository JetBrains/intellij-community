package com.intellij.searchEverywhereMl.ranking.core.id

import com.intellij.ide.actions.searcheverywhere.EvaluationResult

private class CalculatorKeyProvider: ElementKeyForIdProvider {
  override fun getKey(element: Any): Any? = element as? EvaluationResult
}
