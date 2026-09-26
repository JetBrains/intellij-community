@file:Suppress("ReplaceGetOrSet")

package com.intellij.tools.ide.metrics.collector.telemetry

internal class OpentelemetrySpanJsonParserWithChildrenFiltering(
  spanFilter: SpanFilter,
  private val childFilter: SpanFilter,
) : OpentelemetrySpanJsonParser(spanFilter) {
  override fun processChild(result: MutableSet<SpanElement>, parent: SpanElement, index: Map<String, Collection<SpanData>>) {
    index.get(parent.spanId)?.forEach {
      val span = toSpanElement(it)
      if (parent.isWarmup) {
        span.isWarmup = true
      }
      if (!childFilter.filter(span)) {
        return@forEach
      }
      result.add(span)
      processChild(result, span, index)
    }
  }
}

