package com.intellij.metricsCollector.metrics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IDETestContext.Companion.OPENTELEMETRY_FILE
import com.intellij.metricsCollector.collector.PerformanceMetrics.Metric
import com.intellij.metricsCollector.collector.PerformanceMetrics.MetricId.Counter
import com.intellij.metricsCollector.collector.PerformanceMetrics.MetricId.Duration
import com.intellij.metricsCollector.collector.PerformanceMetricsDto
import com.intellij.openapi.util.BuildNumber
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

const val TOTAL_TEST_TIMER_NAME: String = "test"
const val DEFAULT_SPAN_NAME: String = "performance_test"

data class MetricWithAttributes(val metric: Metric<*>,
                                val attributes: MutableList<Metric<*>> = mutableListOf())

fun getOpenTelemetry(context: IDETestContext): PerformanceMetricsDto {
  return getOpenTelemetry(context, DEFAULT_SPAN_NAME)
}

fun getOpenTelemetry(context: IDETestContext, vararg spansNames: String): PerformanceMetricsDto {
  val metrics = spansNames.map { spanName -> getMetrics(context, spanName) }.flatten()
  return PerformanceMetricsDto.create(
    projectName = context.testName,
    buildNumber = BuildNumber.fromStringWithProductCode(context.ide.build, context.ide.productCode)!!,
    metrics = metrics
  )
}

fun findMetricValue(metrics: List<Metric<*>>, metric: Duration) = metrics.first { it.id.name == metric.name }.value

/**
 * The method reports duration of `nameSpan` and all its children spans.
 * Besides, all attributes are reported as counters.
 */
fun getMetrics(file: File, nameOfSpan: String): MutableCollection<Metric<*>> {
  val root = jacksonObjectMapper().readTree(file)
  val spanToMetricMap = mutableMapOf<String, MutableList<MetricWithAttributes>>()
  val allSpans = root.get("data")[0].get("spans")
  for (span in allSpans) {
    if (span.get("operationName").textValue() == nameOfSpan) {
      val metric = MetricWithAttributes(Metric(Duration(nameOfSpan), getDuration(span)))
      populateAttributes(metric, span)
      spanToMetricMap.getOrPut(nameOfSpan) { mutableListOf() }.add(metric)
      processChildren(spanToMetricMap, allSpans, span.get("spanID").textValue())
    }
  }
  spanToMetricMap.remove(DEFAULT_SPAN_NAME)
  return combineMetrics(spanToMetricMap)
}

/**
 * The method reports duration of `nameSpan` and all its children spans.
 * Besides, all attributes are reported as counters.
 */
fun getMetrics(context: IDETestContext, nameOfSpan: String): MutableCollection<Metric<*>> {
  return getMetrics(context.paths.logsDir.resolve(OPENTELEMETRY_FILE).toFile(), nameOfSpan)
}

private fun combineMetrics(metrics: MutableMap<String, MutableList<MetricWithAttributes>>): MutableCollection<Metric<*>> {
  val result = mutableListOf<Metric<*>>()
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
      entry.value.forEach { metric ->
        val value = metric.metric.value.toLong()
        val spanUpdatedName = entry.key + "_$counter"
        result.add(Metric(Duration(spanUpdatedName), value))
        result.addAll(getAttributes(spanUpdatedName, metric))
        counter++
      }
      val sum = entry.value.sumOf { it.metric.value.toLong() }
      val mean = sum / entry.value.size
      val variance = (entry.value
        .map { (it.metric.value.toDouble() - mean.toDouble()).pow(2) }
        .reduce { acc, d -> acc + d }) / (entry.value.size)
      result.add(Metric(Duration(entry.key), sum))
      result.add(Metric(Duration(entry.key +  "#mean_value"), mean))
      result.add(Metric(Duration(entry.key +  "#standard_deviation"), sqrt(variance).toLong()))
    }
  }
  return result
}

private fun getAttributes(spanName: String, metric: MetricWithAttributes): Collection<Metric<*>> {
  return metric.attributes.map { attributeMetric ->
    Metric(Counter("$spanName#" + attributeMetric.id.name), attributeMetric.value.toInt())
  }
}

private fun processChildren(spanToMetricMap: MutableMap<String, MutableList<MetricWithAttributes>>,
                            allSpans: JsonNode,
                            parentSpanId: String?) {
  allSpans.forEach { span ->
    span.get("references")?.forEach { reference ->
      if (reference.get("refType")?.textValue() == "CHILD_OF") {
        val spanId = reference.get("spanID").textValue()
        if (spanId == parentSpanId) {
          val spanName = span.get("operationName").textValue()
          val metric = MetricWithAttributes(Metric(Duration(spanName), getDuration(span)))
          populateAttributes(metric, span)
          spanToMetricMap.getOrPut(spanName) { mutableListOf() }.add(metric)
          processChildren(spanToMetricMap, allSpans, span.get("spanID").textValue())
        }
      }
    }
  }
}

private fun getDuration(span: JsonNode) = (span.get("duration").longValue() / 1000.0).roundToLong()

private fun populateAttributes(metric: MetricWithAttributes, span: JsonNode) {
  span.get("tags")?.forEach { tag ->
    val attributeName = tag.get("key").textValue()
    tag.get("value").textValue().runCatching { toInt() }.onSuccess {
      metric.attributes.add(Metric(Counter(attributeName), it))
    }
  }
}


