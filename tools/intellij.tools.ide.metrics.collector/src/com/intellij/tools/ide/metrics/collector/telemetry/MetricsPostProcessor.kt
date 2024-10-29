package com.intellij.tools.ide.metrics.collector.telemetry

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.metrics.standardDeviation

interface MetricsPostProcessor {
  fun process(groupedMetrics: Map<String, List<MetricWithAttributes>>): List<PerformanceMetrics.Metric>
}

class CombinedMetricsPostProcessor : MetricsPostProcessor {
  override fun process(groupedMetrics: Map<String, List<MetricWithAttributes>>): List<Metric> {
    val result = mutableListOf<Metric>()

    for (entry in groupedMetrics) {
      if (entry.value.size == 1) {
        val metric = entry.value.first()
        result.addAll(getAttributes(entry.key, metric))
        if (metric.metric.id.name != TOTAL_TEST_TIMER_NAME) {
          result.add(metric.metric)
        }
      }
      else {
        var counter = 1
        val mediumAttributes = mutableMapOf<String, MutableList<Int>>()
        for (metric in entry.value) {
          val value = metric.metric.value
          val spanUpdatedName = entry.key + "_$counter"
          result.add(Metric.newDuration(spanUpdatedName, value))
          result.addAll(getAttributes(spanUpdatedName, metric))
          getAttributes(entry.key, metric).forEach {
            val key = it.id.name
            val collection = mediumAttributes.getOrDefault(key, mutableListOf())
            collection.add(it.value)
            mediumAttributes.put(key, collection)
          }
          counter++
        }
        for (attr in mediumAttributes) {
          if (attr.key.endsWith("#max")) {
            result.add(Metric.newDuration(attr.key, attr.value.max()))
            continue
          }
          if (attr.key.endsWith("#p90")) {
            continue
          }
          if (attr.key.endsWith("#mean_value")) {
            result.add(Metric.newDuration(attr.key, attr.value.average().toInt()))
            continue
          }

          result.add(Metric.newCounter(attr.key + "#count", attr.value.size))
          result.add(Metric.newDuration(attr.key + "#mean_value", attr.value.average().toInt()))
          result.add(Metric.newDuration(attr.key + "#standard_deviation", attr.value.standardDeviation()))
        }
        val sum = entry.value.sumOf { it.metric.value }
        val count = entry.value.size
        val mean = sum / count
        val standardDeviation = entry.value.map { it.metric.value }.standardDeviation()
        result.add(Metric.newDuration(entry.key, sum))
        result.add(Metric.newCounter(entry.key + "#count", count))
        result.add(Metric.newDuration(entry.key + "#mean_value", mean))
        result.add(Metric.newDuration(entry.key + "#standard_deviation", standardDeviation))
      }
    }

    return result
  }
}