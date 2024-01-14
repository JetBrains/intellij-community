package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.KEY_NOT_COMPUTED_EVENT
import com.intellij.searchEverywhereMl.ranking.id.MissingKeyProviderCollector.report

/**
 * The collector keeps a set of classes for which we failed to compute a key,
 * then, by calling [report] function, the event is logged with this set.
 */
internal object MissingKeyProviderCollector {
  private val unsupportedClasses = mutableSetOf<Class<*>>()

  fun addMissingProviderForClass(klass: Class<*>) {
    unsupportedClasses.add(klass)
  }

  /**
   * Logs the classes for which we could not compute a key.
   *
   * We pass the responsibility of calling this function outside of this class,
   * to avoid spamming the same over and over again during one search session.
   */
  fun report(sessionId: Int) {
    if (unsupportedClasses.isEmpty()) {
      return
    }
    KEY_NOT_COMPUTED_EVENT.log(sessionId, unsupportedClasses.toList())
    unsupportedClasses.clear()
  }
}