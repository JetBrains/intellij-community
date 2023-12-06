package com.intellij.turboComplete.ranking

import com.intellij.platform.ml.impl.turboComplete.ranking.KindRankingListener
import com.intellij.platform.ml.impl.turboComplete.ranking.RankedKind

interface DelegatingKindRankingListener<T : KindRankingListener> : KindRankingListener {
  val delegatedListeners: MutableList<T>

  override fun onRankingStarted() {
    delegatedListeners.forEach { it.onRankingStarted() }
  }

  override fun onRanked(ranked: List<RankedKind>) {
    delegatedListeners.forEach { it.onRanked(ranked) }
  }

  override fun onRankingFinished() {
    delegatedListeners.forEach { it.onRankingFinished() }
  }
}