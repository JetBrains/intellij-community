// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

class PerformanceTracker {
  private var sortingCount = 0

  private val measures: ConcurrentHashMap<String, LongAdder> = ConcurrentHashMap()
  private val elementProvidersMeasurer: ConcurrentHashMap<String, LongAdder> = ConcurrentHashMap()

  private var totalMlContribution: Long = 0L

  fun totalMLTimeContribution(): Long = totalMlContribution

  fun sortingPerformed(itemsCount: Int, totalTime: Long) {
    addByKey("sorting.items.$sortingCount", itemsCount.toLong())
    addByKey("sorting.time.$sortingCount", totalTime)
    totalMlContribution += totalTime
    sortingCount += 1
  }

  fun eventLogged(eventType: String, timeSpent: Long) {
    addByKey("log.$eventType", timeSpent)
    addByKey("log.total", timeSpent)
  }

  fun contextFeaturesCalculated(providerName: String, timeSpent: Long) {
    addByKey("context.features.$providerName", timeSpent)
  }

  fun itemsScored(itemsCount: Int, timeSpent: Long) {
    addByKey("model.items.$sortingCount", itemsCount.toLong())
    addByKey("model.time.$sortingCount", timeSpent)
  }

  fun reorderedByML() {
    addByKey("reordered.by.ml", 1)
  }

  fun measurements(): Map<String, Long> {
    flushElementProvidersContribution()
    return measures.mapValues { it.value.toLong() }
  }

  fun <T> trackElementFeaturesCalculation(providerName: String, compute: () -> T): T {
    val started = System.nanoTime()
    val result = compute()
    elementProvidersMeasurer.computeIfAbsent(providerName) { LongAdder() }.add(System.nanoTime() - started)
    return result
  }

  private fun flushElementProvidersContribution() {
    elementProvidersMeasurer.forEach { addByKey("element.features.${it.key}", TimeUnit.NANOSECONDS.toMillis(it.value.toLong())) }
    elementProvidersMeasurer.clear()
  }

  private fun addByKey(key: String, value: Long) {
    if (value != 0L) {
      measures.computeIfAbsent(key) { LongAdder() }.add(value)
    }
  }
}