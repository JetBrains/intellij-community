package com.intellij.ide.actions.searcheverywhere.ml.features.statistician

import com.intellij.psi.statistics.Statistician

internal abstract class SearchEverywhereStatistician<T> : Statistician<T, String>() {
  abstract fun getContext(element: Any): String?
}
