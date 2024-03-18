package com.intellij.tools.ide.metrics.collector.telemetry

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.withRetry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

open class OpentelemetryJsonParser(private val spanFilter: SpanFilter) {
  private fun getSpans(file: Path): JsonNode {
    val spanData: JsonNode? = withRetry(messageOnFailure = "Failure during spans extraction from OpenTelemetry json file",
                                        retries = 5,
                                        printFailuresMode = PrintFailuresMode.ONLY_LAST_FAILURE,
                                        delay = 300.milliseconds) {
      val root = Files.newInputStream(file).use {
        jacksonObjectMapper().readTree(it)
      }
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
    if (allSpans == null || allSpans.isEmpty) {
      throw IllegalStateException("No spans was found")
    }
    return allSpans
  }

  private fun getParentToSpanMap(file: Path): Map<String, Set<SpanElement>> {
    val indexParentToChild = LinkedHashMap<String, MutableSet<SpanElement>>()
    val spans = getSpans(file)
    for (span in spans) {
      val parentSpanId = span.getParentSpanId()
      if (parentSpanId != null) {
        indexParentToChild.computeIfAbsent(parentSpanId) { LinkedHashSet() }.add(span.toSpanElement())
      }
    }
    return indexParentToChild
  }

  fun getSpanElements(file: Path): Sequence<SpanElement> {
    val spans = getSpanElements(getSpans(file))
    val index = getParentToSpanMap(file)
    val filter = spans.filter { spanElement -> spanFilter.filter(spanElement) }
    val result = LinkedHashSet<SpanElement>()
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