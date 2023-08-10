package com.intellij.turboComplete.analysis.usage

import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import java.util.*

data class ValuePerPeriod<T>(
  val period: Int,
  val value: T,
)

private data class RecentUsage<T>(
  val isCreation: Boolean,
  val action: (KindUsageTracker<T>) -> Unit,
)

class KindRecentUsageTracker<T>(override val kind: CompletionKind,
                                private val windowSize: Int,
                                val baseTrackerProvider: (CompletionKind) -> KindUsageTracker<T>
) : KindUsageTracker<ValuePerPeriod<T>> {
  private val recentUsages: Queue<RecentUsage<T>> = LinkedList()
  private var recentCreations: Int = 0
  private var recentUsagesStatistics: T? = null

  private fun popCreationAndFollowingActions() {
    assert(recentUsages.peek().isCreation)
    assert(recentUsages.remove().isCreation)
    recentCreations -= 1
    while (recentUsages.isNotEmpty() && !recentUsages.peek().isCreation) {
      recentUsages.remove()
    }
  }

  private fun trackRecentUsage(action: RecentUsage<T>) {
    recentUsagesStatistics = null
    recentUsages.add(action)
    if (action.isCreation) {
      recentCreations += 1
    }
    while (recentCreations > windowSize) {
      popCreationAndFollowingActions()
    }
  }

  override fun trackCreated() {
    trackRecentUsage(RecentUsage(true) { it.trackCreated() })
  }

  override fun trackGenerated(correct: Boolean) {
    trackRecentUsage(RecentUsage(false) { it.trackGenerated(correct) })
  }

  private fun computeSummary(): T {
    val recentUsagesTracker = baseTrackerProvider(kind)
    recentUsages.forEach { it.action(recentUsagesTracker) }
    return recentUsagesTracker.getSummary()
  }

  override fun getSummary(): ValuePerPeriod<T> {
    if (recentUsagesStatistics == null) {
      recentUsagesStatistics = computeSummary()
    }
    return ValuePerPeriod(recentCreations, recentUsagesStatistics!!)
  }
}