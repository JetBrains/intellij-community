package com.intellij.turboComplete.ranking

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import com.intellij.platform.ml.impl.turboComplete.KindVariety
import com.intellij.platform.ml.impl.turboComplete.ranking.KindRankingListener
import com.intellij.platform.ml.impl.turboComplete.ranking.RankedKind

class ReportingKindSorter(
  private val listener: KindRankingListener,
  private val sorterProvider: KindSorterProvider
) : KindRelevanceSorter {
  override val kindVariety: KindVariety
    get() = sorterProvider.kindVariety

  override fun sort(kinds: List<CompletionKind>, parameters: CompletionParameters): List<RankedKind>? {
    val sorter = sorterProvider.createSorter()
    listener.onRankingStarted()
    return try {
      val kindsSorted = sorter.sort(kinds, parameters)
      kindsSorted?.let {listener.onRanked(it) }
      kindsSorted
    }
    finally {
      listener.onRankingFinished()
    }
  }
}