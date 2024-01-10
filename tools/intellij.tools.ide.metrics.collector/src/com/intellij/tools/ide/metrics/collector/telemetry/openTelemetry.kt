package com.intellij.tools.ide.metrics.collector.telemetry

import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.metrics.standardDeviation
import com.intellij.util.alsoIfNull
import java.nio.file.Path

const val TOTAL_TEST_TIMER_NAME: String = "test"
const val DEFAULT_SPAN_NAME: String = "performance_test"

data class MetricWithAttributes(val metric: Metric,
                                val attributes: MutableList<Metric> = mutableListOf())

private val logger = logger<OpentelemetryJsonParser>()

private fun applyAliases(spanElements: List<SpanElement>, aliases: Map<String, String>): List<SpanElement> {
  return spanElements.map {
    if (aliases.containsKey(it.name)) {
      it.copy(name = aliases[it.name]!!)
    }
    else {
      it
    }
  }
}

/**
 * Reports duration of `nameSpan` and all its children spans.
 * Replaces the names with an alias, if one was passed.
 * Besides, all attributes are reported as counters.
 * If there are multiple values with the same name:
 * 1. They will be re-numbered `<value>_1`, `<value>_2`, etc. and the sum will be recorded as `<value>`.
 * 2. In the sum value, mean value and standard deviation of attribute value will be recorded
 * 2a. If attribute ends with `#max`, in sum the max of max will be recorded
 * 3a. If attribute ends with `#mean_value`, the mean value of mean values will be recorded
 */
fun getMetricsFromSpanAndChildren(file: Path,
                                  filter: SpanFilter,
                                  metricSpanProcessor: MetricSpanProcessor = MetricSpanProcessor(),
                                  aliases: Map<String, String> = mapOf()): List<Metric> {
  val spanElements = OpentelemetryJsonParser(filter).getSpanElements(file).toList()
    .let {
      if (aliases.isNotEmpty()) {
        applyAliases(it, aliases)
      }
      else {
        it
      }
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
  val spanElements = OpentelemetryJsonParser(SpanFilter.containsNameIn(listOf(fromSpanName, toSpanName))).getSpanElements(file)
  val spanToMetricMap = spanElements
    .map { betweenSpanProcessor.process(it) }
    .filterNotNull()
    .groupBy { it.name }
  val fromSpanMetrics = spanToMetricMap[fromSpanName] ?: throw IllegalStateException("Spans $fromSpanName is null")
  val toSpanMetrics = spanToMetricMap[toSpanName] ?: throw IllegalStateException("Spans $toSpanName is null")
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

fun getSpansMetricsMap(file: Path, spanFilter: SpanFilter = SpanFilter { true }): Map<String, List<MetricWithAttributes>> {
  val spanElements = OpentelemetryJsonParser(spanFilter).getSpanElements(file)
  val metricSpanProcessor = MetricSpanProcessor()
  val spanToMetricMap = spanElements.map { metricSpanProcessor.process(it) }
    .filterNotNull()
    .groupBy { it.metric.id.name }
  return spanToMetricMap
}

fun getMetricsForStartup(file: Path): List<Metric> {
  val spansToPublish = listOf("bootstrap", "startApplication", "ProjectImpl container")
  val spansSuffixesToIgnore = listOf(": scheduled", ": completing")
  val filter = SpanFilter.containsNameIn(spansToPublish)
  val childFilter = SpanFilter { span -> spansSuffixesToIgnore.none { span.name.endsWith(it) } }

  val spanElements = OpentelemetryJsonParserWithChildrenFiltering(filter, childFilter).getSpanElements(file)
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
  metrics.forEach { entry ->
    if (entry.value.size == 1) {
      val metric = entry.value.first()
      result.addAll(getAttributes(entry.key, metric))
      if (metric.metric.id.name != TOTAL_TEST_TIMER_NAME) {
        result.add(metric.metric)
      }
    }
    else {
      var counter = 1
      val mediumAttributes: MutableMap<String, MutableList<Long>> = mutableMapOf()
      entry.value.forEach { metric ->
        val value = metric.metric.value
        val spanUpdatedName = entry.key + "_$counter"
        result.add(Metric.newDuration(spanUpdatedName, value))
        result.addAll(getAttributes(spanUpdatedName, metric))
        getAttributes(entry.key, metric).forEach {
          val key = it.id.name
          val collection = mediumAttributes.getOrDefault(key, mutableListOf())
          collection.add(it.value)
          mediumAttributes[key] = collection
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

        result.add(Metric.newDuration(attr.key + "#mean_value", attr.value.average().toLong()))
        result.add(Metric.newDuration(attr.key + "#standard_deviation", attr.value.standardDeviation()))
      }
      val sum = entry.value.sumOf { it.metric.value }
      val mean = sum / entry.value.size
      val standardDeviation = entry.value.map { it.metric.value }.standardDeviation()
      result.add(Metric.newDuration(entry.key, sum))
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
