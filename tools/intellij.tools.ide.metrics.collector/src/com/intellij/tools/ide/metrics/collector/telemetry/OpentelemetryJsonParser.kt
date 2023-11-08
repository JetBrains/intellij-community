package com.intellij.tools.ide.metrics.collector.telemetry

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.withRetry
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

open class OpentelemetryJsonParser(private val spanFilter: SpanFilter) {

  private fun getSpans(file: File): JsonNode {
    val spanData: JsonNode? = withRetry(messageOnFailure = "Failure during spans extraction from OpenTelemetry json file",
                                        retries = 5,
                                        printFailuresMode = PrintFailuresMode.ONLY_LAST_FAILURE,
                                        delay = 300.milliseconds) {
      val json = file.readText()

      val root = jacksonObjectMapper().readTree(json)
      val data = root.get("data")
      if (data == null || data.isEmpty) {
        throw IllegalArgumentException("No 'data' node in json at path $file")
      }
      if (data[0] == null || data[0].isEmpty) {
        throw IllegalArgumentException("First data element is absent in json file $file")
      }

      return@withRetry data
    }

    val allSpans = spanData!![0].get("spans")
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
    val filter = spans.filter { spanElement -> spanFilter.filter(spanElement) }
    val result = mutableSetOf<SpanElement>()
    filter.forEach {
      result.add(it)
      processChild(result, it, index)
    }
    return result.asSequence()
  }

  protected open fun processChild(result: MutableSet<SpanElement>, parent: SpanElement, index: Map<String, Collection<SpanElement>>) {
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