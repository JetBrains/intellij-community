package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLStatisticsCollector.COLLECTED_RESULTS_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.get
import com.jetbrains.fus.reporting.model.lion3.LogEvent

fun Iterable<LogEvent>.firstEventWithCollectedItems() = this.first { it.event.data[COLLECTED_RESULTS_DATA_KEY].isNotEmpty() }

fun LogEvent.firstElement() = this.event.data[COLLECTED_RESULTS_DATA_KEY].first()
