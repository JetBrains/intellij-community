package com.intellij.ide.actions.searcheverywhere.ml.features.statistician

import com.intellij.psi.statistics.Statistician

internal abstract class SearchEverywhereStatistician<T> : Statistician<T, String>() {
  protected val contextPrefix = "searchEverywhere"

  abstract fun getContext(element: T): String?
}
