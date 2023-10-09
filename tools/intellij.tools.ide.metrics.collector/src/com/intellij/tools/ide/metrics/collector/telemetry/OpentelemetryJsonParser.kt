package com.intellij.tools.ide.metrics.collector.telemetry

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

class OpentelemetryJsonParser(private val spanFilter: SpanFilter) {

  private fun getSpans(file: File): JsonNode {
    val json = file.readText()
    //TODO workaround for non valid intermediate json
    val root = jacksonObjectMapper().readTree(if (json.endsWith(",")) json else "$json]}]}")
    val data = root.get("data")
    if (data == null || data.isEmpty) {
      throw IllegalArgumentException("Not 'data' node in json")
    }
    if (data[0] == null || data[0].isEmpty) {
      throw IllegalArgumentException("First data element is absent")
    }
    val allSpans = data[0].get("spans")
    if (allSpans == null || allSpans.isEmpty)
      throw IllegalStateException("No spans was found")
    return allSpans
  }

  private fun getParentToSpansMap(file: File): Map<String, Set<SpanElement>> {
    val indexParentToChild = mutableMapOf<String, MutableSet<SpanElement>>()
    val spans = getSpans(file)
    for (span in spans) {
      val parentSpanId = span.getParentSpanId()
      if (parentSpanId != null) {
        indexParentToChild.getOrPut(parentSpanId) { mutableSetOf() }.add(span.toSpanElement())
      }
    }
    return indexParentToChild
  }

  fun getSpanElements(file: File): Sequence<SpanElement> {
    val spans = getSpanElements(getSpans(file))
    val index = getParentToSpansMap(file)
    val filter = spans.filter { spanElement -> spanFilter.filter.invoke(spanElement.name) }
    val result = mutableSetOf<SpanElement>()
    filter.forEach {
      result.add(it)
      processChild(result, it, index)
    }
    return result.asSequence()
  }

  private fun processChild(result: MutableSet<SpanElement>, parent: SpanElement, index: Map<String, Collection<SpanElement>>) {
    index[parent.spanId]?.forEach {
      if (parent.isWarmup) {
        it.isWarmup = true
      }
      result.add(it)
      processChild(result, it, index)
    }
  }

  private fun getSpanElements(node: JsonNode): Sequence<SpanElement> {
    return node.iterator().asSequence().map { it.toSpanElement() }
  }
}