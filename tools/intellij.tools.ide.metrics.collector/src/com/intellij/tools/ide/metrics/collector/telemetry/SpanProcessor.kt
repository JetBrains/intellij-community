package com.intellij.tools.ide.metrics.collector.telemetry

import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import kotlin.time.Duration

interface SpanProcessor<T> {

  /**
   * Processes the given `span` element.
   *
   * @param span The `SpanElement` to process.
   * @return The result of the processing, or `null` if no result is available.
   */
  fun process(span: SpanElement): T?

}

class MetricSpanProcessor(val ignoreWarmupSpan: Boolean = true) : SpanProcessor<MetricWithAttributes> {
  override fun process(span: SpanElement): MetricWithAttributes? {
    if (span.isWarmup != ignoreWarmupSpan && (span.duration > Duration.ZERO || !shouldAvoidIfZero(span))) {
      val metrics = MetricWithAttributes(PerformanceMetrics.newDuration(span.name, span.duration.inWholeMilliseconds.toInt()))
      populateAttributes(metrics, span)
      return metrics
    }
    return null
  }
}

private fun shouldAvoidIfZero(span: SpanElement): Boolean {
  span.tags.forEach { tag ->
    val attributeName = tag.first
    if (attributeName == "avoid_null_value") {
      tag.second.runCatching { toBoolean() }.onSuccess {
        return it
      }
    }
  }
  return false
}

fun populateAttributes(metric: MetricWithAttributes, span: SpanElement) {
  span.tags.forEach { tag ->
    val attributeName = tag.first
    tag.second.runCatching { toInt() }.onSuccess {
      metric.attributes.add(PerformanceMetrics.newCounter((attributeName), it))
    }
  }
}