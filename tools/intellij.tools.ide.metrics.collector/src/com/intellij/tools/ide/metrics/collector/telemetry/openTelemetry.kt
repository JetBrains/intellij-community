@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.tools.ide.metrics.collector.telemetry

import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import java.nio.file.Path
import kotlin.math.min

const val TOTAL_TEST_TIMER_NAME: String = "test"
const val DEFAULT_SPAN_NAME: String = "performance_test"

data class MetricWithAttributes(
  @JvmField val metric: Metric,
  @JvmField val attributes: MutableList<Metric> = mutableListOf(),
)

private val logger = logger<OpentelemetrySpanJsonParser>()

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
  val spanElements = OpentelemetrySpanJsonParser(SpanFilter.nameInList(listOf(fromSpanName, toSpanName))).getSpanElements(file)
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
  val spanIds = sortedFromSpans.map { it.spanId to it.parentSpanId }.flatMap { listOf(it.first, it.second) }.toSet()
  val sortedToSpans = toSpanMetrics.sortedByDescending { info -> info.startTimestamp }.filter { spanIds.contains(it.parentSpanId) }
  val minSize = min(fromSpanMetrics.size, sortedToSpans.size)
  for (i in 0.. minSize - 1) {
    val currentToSpan = sortedToSpans[i]
    val currentFromSpan = sortedFromSpans[i]
    if (currentFromSpan.spanId != currentToSpan.parentSpanId) {
      logger.warn(
        "Current span $fromSpanName with spanId ${currentToSpan.spanId} have ${currentToSpan.parentSpanId}, but expected ${currentFromSpan.spanId}")
    }
    val duration = java.time.Duration.between(currentFromSpan.startTimestamp, currentToSpan.startTimestamp).plusNanos(currentToSpan.duration.inWholeNanoseconds)
    val metric = MetricWithAttributes(Metric.newDuration(name, duration.toMillis().toInt()))
    metrics.add(metric)
  }
  return CombinedMetricsPostProcessor().process(mapOf(name to metrics))
}

/**
 * Delta metrics between the `<phase>.before` and `<phase>.after` marks, one counter per cumulative
 * attribute.
 *
 * Reads the marks emitted by the `%mark` command (instant events in the `mark` scope) and, for every
 * numeric attribute whose key starts with [keyPrefix] and is present on both marks, publishes
 * `Metric.newCounter("<phase>#<key>", after - before)`. Following the platform's `#` attribute
 * convention (for example `localInspections#Warnings`), each counter is exposed as an attribute of
 * the `<phase>` metric and the raw attribute key is kept verbatim (no renaming).
 *
 * [keyPrefix] both selects the counters to diff and excludes bookkeeping attributes such as the
 * `finish` timestamp that every mark carries. Values round-trip through `opentelemetry.json` as
 * strings, so a value that does not parse as `Long` is skipped. The attributes are assumed to be
 * cumulative counters captured before → after; each delta is narrowed to `Int`.
 *
 * @throws IllegalStateException when either mark is absent from [file].
 */
fun getDeltaMetricsBetweenMarks(
  file: Path,
  phase: String,
  keyPrefix: String = "jvm.",
): List<Metric> {
  val fromSpanName = "$phase.before"
  val toSpanName = "$phase.after"
  val spans = OpentelemetrySpanJsonParser(SpanFilter.nameInList(fromSpanName, toSpanName))
    .getSpanElements(file)
    .sortedBy { it.startTimestamp }
  val fromSpan = spans.firstOrNull { it.name == fromSpanName } ?: throw IllegalStateException("Mark $fromSpanName not found in $file")
  val toSpan = spans.firstOrNull { it.name == toSpanName } ?: throw IllegalStateException("Mark $toSpanName not found in $file")

  return toSpan.tags.mapNotNull { (key, rawValue) ->
    if (!key.startsWith(keyPrefix)) return@mapNotNull null
    val to = rawValue.toLongOrNull() ?: return@mapNotNull null
    val from = longTag(fromSpan, key) ?: return@mapNotNull null
    Metric.newCounter("$phase#$key", (to - from).toInt())
  }
}

private fun longTag(span: SpanElement, key: String): Long? {
  return span.tags.firstOrNull { it.first == key }?.second?.toLongOrNull()
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
  return spanElements[0].startTimestamp.toEpochMilli()
}

fun getMetricsForStartup(file: Path): List<Metric> {
  val spansToPublish = listOf("bootstrap", "startApplication", "ProjectImpl container")
  val spansSuffixesToIgnore = listOf(": scheduled", ": completing")
  val filter = SpanFilter.nameInList(spansToPublish)
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
  val startMetrics = spanElementsWithoutRoots.map { span -> Metric.newDuration(span.name + ".start", java.time.Duration.between(startTime, span.startTimestamp).toMillis().toInt()) }
  val endMetrics = spanElementsWithoutRoots.map { span ->
    Metric.newDuration(span.name + ".end", java.time.Duration.between(startTime, span.startTimestamp).plusNanos(span.duration.inWholeNanoseconds).toMillis().toInt())
  }
  return CombinedMetricsPostProcessor().process(spanToMetricMap) + startMetrics + endMetrics
}

internal fun getAttributes(spanName: String, metric: MetricWithAttributes): Collection<Metric> {
  return metric.attributes.map { attributeMetric ->
    val attributeMetricName = "$spanName#" + attributeMetric.id.name
    when (attributeMetric.id) {
      is PerformanceMetrics.MetricId.Duration -> Metric.newDuration(attributeMetricName, attributeMetric.value)
      is PerformanceMetrics.MetricId.Counter -> Metric.newCounter(attributeMetricName, attributeMetric.value)
    }
  }
}

fun getOrderedSpans(openTelemetryFile: Path, spanName: String): List<SpanElement> =
  OpentelemetrySpanJsonParser(SpanFilter.nameEquals(spanName)).getSpanElements(openTelemetryFile)
    .filter { it.name == spanName }.toList().sortedBy { it.startTimestamp }
