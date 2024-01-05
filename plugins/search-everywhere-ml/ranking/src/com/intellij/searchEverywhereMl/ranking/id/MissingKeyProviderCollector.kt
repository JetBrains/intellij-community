package com.intellij.searchEverywhereMl.ranking.id

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.ClassListEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.Fields.GROUP
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.Fields.SESSION_ID_LOG_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.id.MissingKeyProviderCollector.report

/**
 * The collector keeps a set of classes for which we failed to compute a key,
 * then, by calling [report] function, the event is logged with this set.
 */
object MissingKeyProviderCollector : CounterUsagesCollector() {
  private val CLASSES_WITHOUT_KEY_PROVIDERS_FIELD = ClassListEventField("unsupported_classes")
  private val KEY_NOT_COMPUTED_EVENT = GROUP.registerEvent("key.not.computed",
                                                           SESSION_ID_LOG_DATA_KEY,
                                                           CLASSES_WITHOUT_KEY_PROVIDERS_FIELD)
  private val unsupportedClasses = mutableSetOf<Class<*>>()

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

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
    KEY_NOT_COMPUTED_EVENT.log(sessionId, unsupportedClasses.toList())
    unsupportedClasses.clear()
  }
}