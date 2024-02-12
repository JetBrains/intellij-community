package com.intellij.searchEverywhereMl.ranking

import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector.COLLECTED_RESULTS_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.get
import com.jetbrains.fus.reporting.model.lion3.LogEvent

fun Iterable<LogEvent>.firstEventWithCollectedItems() = this.first { it.event.data[COLLECTED_RESULTS_DATA_KEY].isNotEmpty() }

fun LogEvent.firstElement() = this.event.data[COLLECTED_RESULTS_DATA_KEY].first()
