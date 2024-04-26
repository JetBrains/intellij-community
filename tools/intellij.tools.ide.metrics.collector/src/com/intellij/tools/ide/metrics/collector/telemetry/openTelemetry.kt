@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.tools.ide.metrics.collector.telemetry

import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.metrics.standardDeviation
import java.nio.file.Path

const val TOTAL_TEST_TIMER_NAME: String = "test"
const val DEFAULT_SPAN_NAME: String = "performance_test"

data class MetricWithAttributes(
  @JvmField val metric: Metric,
  @JvmField val attributes: MutableList<Metric> = mutableListOf(),
)

private val logger = logger<OpentelemetrySpanJsonParser>()

/**
 * Reports duration of `nameSpan` and all its children spans.
 * Replaces the names with an alias if one was passed.
 * Besides, all attributes are reported as counters.
 * If there are multiple values with the same name:
 * 1. They will be re-numbered `<value>_1`, `<value>_2`, etc. and the sum will be recorded as `<value>`.
 * 2. In the sum value, mean value and standard deviation of attribute value will be recorded
 * 2a. If attribute ends with `#max`, in a sum the max of max will be recorded
 * 3a. If attribute ends with `#mean_value`, the mean value of mean values will be recorded
 */
fun getMetricsFromSpanAndChildren(file: Path,
                                  filter: SpanFilter,
                                  metricSpanProcessor: MetricSpanProcessor = MetricSpanProcessor(),
                                  aliases: Map<String, String> = mapOf()): List<Metric> {
  val spanElements = OpentelemetrySpanJsonParser(filter).getSpanElements(file).map {
    val name = aliases.getOrDefault(it.name, it.name)
    if (name != it.name) {
      return@map it.copy(name = name)
    }
    return@map it
  }

  val spanToMetricMap = spanElements.mapNotNull { metricSpanProcessor.process(it) }
    .groupBy { it.metric.id.name }
  return combineMetrics(spanToMetricMap)
}

/**
 * Returns a list of metrics based on the difference between two spans.
 *
 * @param name The name of the metric.
 * @param file The file containing the spans.
 * @param fromSpanName The name of the "from" span.
 * @param toSpanName The name of the "to" span.
 * @return A list of metrics.
 * @throws IllegalStateException if the fromSpan or toSpan is null.
 * @throws IllegalArgumentException if the size of the toSpans is not greater than or equal to the size of the fromSpans.
 */
fun getMetricsBasedOnDiffBetweenSpans(name: String, file: Path, fromSpanName: String, toSpanName: String): List<Metric> {
  val betweenSpanProcessor = SpanInfoProcessor()
  val spanElements = OpentelemetrySpanJsonParser(SpanFilter.containsNameIn(listOf(fromSpanName, toSpanName))).getSpanElements(file)
  val spanToMetricMap = spanElements
    .mapNotNull { betweenSpanProcessor.process(it) }
    .groupBy { it.name }
  val fromSpanMetrics = spanToMetricMap.get(fromSpanName) ?: throw IllegalStateException("Spans $fromSpanName is null")
  val toSpanMetrics = spanToMetricMap.get(toSpanName) ?: throw IllegalStateException("Spans $toSpanName is null")
  assert(toSpanMetrics.size >= fromSpanMetrics.size) {
    "Size of toSpans (${toSpanMetrics.size}) must be >= size of fromSpans(${fromSpanMetrics.size})"
  }
  val metrics = mutableListOf<MetricWithAttributes>()
  val sortedFromSpans = fromSpanMetrics.sortedByDescending { info -> info.startTimestamp }
  val spanIds = sortedFromSpans.map { it.spanId }.toSet()
  val sortedToSpans = toSpanMetrics.sortedByDescending { info -> info.startTimestamp }.filter { spanIds.contains(it.parentSpanId) }
  for (i in fromSpanMetrics.indices) {
    val currentToSpan = sortedToSpans[i]
    val currentFromSpan = sortedFromSpans[i]
    if (currentFromSpan.spanId != currentToSpan.parentSpanId) {
      logger.warn(
        "Current span $fromSpanName with spanId ${currentToSpan.spanId} have ${currentToSpan.parentSpanId}, but expected ${currentFromSpan.spanId}")
    }
    val duration = currentToSpan.startTimestamp - currentFromSpan.startTimestamp + currentToSpan.duration
    val metric = MetricWithAttributes(Metric.newDuration(name, duration))
    metrics.add(metric)
  }
  return combineMetrics(mapOf(name to metrics))
}

fun getSpansMetricsMap(file: Path, spanFilter: SpanFilter = SpanFilter.any()): Map<String, List<MetricWithAttributes>> {
  val spanElements = OpentelemetrySpanJsonParser(spanFilter).getSpanElements(file)
  val metricSpanProcessor = MetricSpanProcessor()
  val spanToMetricMap = spanElements
    .mapNotNull { metricSpanProcessor.process(it) }
    .groupBy { it.metric.id.name }
  return spanToMetricMap
}

/**
 * Returns timestamp of event defined as com.intellij.diagnostic.StartUpMeasurer.getCurrentTime
 */
fun getStartupTimestampMs(file: Path): Long {
  val spanElements = OpentelemetrySpanJsonParserWithChildrenFiltering(SpanFilter.nameEquals("bootstrap"), SpanFilter.none())
    .getSpanElements(file).filter { it.name == "bootstrap" }.toList()
  if (spanElements.size != 1) throw IllegalStateException("Unexpected number of \"bootstrap\" spans: ${spanElements.size}")
  return spanElements[0].startTimestamp
}

fun getMetricsForStartup(file: Path): List<Metric> {
  val spansToPublish = listOf("bootstrap", "startApplication", "ProjectImpl container")
  val spansSuffixesToIgnore = listOf(": scheduled", ": completing")
  val filter = SpanFilter.containsNameIn(spansToPublish)
  val childFilter = SpanFilter(
    filter = { span -> spansSuffixesToIgnore.none { span.name.endsWith(it) } },
    rawFilter = { span -> spansSuffixesToIgnore.none { span.operationName.endsWith(it) } },
  )

  val spanElements = OpentelemetrySpanJsonParserWithChildrenFiltering(filter, childFilter).getSpanElements(file)
  val startTime = spanElements.first { it.name == "bootstrap" }.startTimestamp
  val spansWithoutDuplicatedNames = spanElements.groupBy { it.name }.filter { it.value.size == 1 }.flatMap { it.value }

  val metricSpanProcessor = MetricSpanProcessor()
  val spanToMetricMap = spansWithoutDuplicatedNames.mapNotNull { metricSpanProcessor.process(it) }.groupBy { it.metric.id.name }

  val spanElementsWithoutRoots = spansWithoutDuplicatedNames.filterNot { it.name in spansToPublish }
  val startMetrics = spanElementsWithoutRoots.map { span -> Metric.newDuration(span.name + ".start", span.startTimestamp - startTime) }
  val endMetrics = spanElementsWithoutRoots.map { span ->
    Metric.newDuration(span.name + ".end", span.startTimestamp - startTime + span.duration)
  }
  return combineMetrics(spanToMetricMap) + startMetrics + endMetrics
}

private fun combineMetrics(metrics: Map<String, List<MetricWithAttributes>>): List<Metric> {
  val result = mutableListOf<Metric>()
  for (entry in metrics) {
    if (entry.value.size == 1) {
      val metric = entry.value.first()
      result.addAll(getAttributes(entry.key, metric))
      if (metric.metric.id.name != TOTAL_TEST_TIMER_NAME) {
        result.add(metric.metric)
      }
    }
    else {
      var counter = 1
      val mediumAttributes = mutableMapOf<String, MutableList<Long>>()
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
          result.add(Metric.newDuration(attr.key, attr.value.average().toLong()))
          continue
        }

        result.add(Metric.newCounter(attr.key + "#count", attr.value.size.toLong()))
        result.add(Metric.newDuration(attr.key + "#mean_value", attr.value.average().toLong()))
        result.add(Metric.newDuration(attr.key + "#standard_deviation", attr.value.standardDeviation()))
      }
      val sum = entry.value.sumOf { it.metric.value }
      val count = entry.value.size
      val mean = sum / count
      val standardDeviation = entry.value.map { it.metric.value }.standardDeviation()
      result.add(Metric.newDuration(entry.key, sum))
      result.add(Metric.newCounter(entry.key + "#count", count.toLong()))
      result.add(Metric.newDuration(entry.key + "#mean_value", mean))
      result.add(Metric.newDuration(entry.key + "#standard_deviation", standardDeviation))
    }
  }
  return result
}

private fun getAttributes(spanName: String, metric: MetricWithAttributes): Collection<Metric> {
  return metric.attributes.map { attributeMetric ->
    Metric.newCounter("$spanName#" + attributeMetric.id.name, attributeMetric.value)
  }
}
