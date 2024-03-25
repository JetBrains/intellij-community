@file:Suppress("ReplaceGetOrSet", "OPT_IN_USAGE", "SSBasedInspection")

package com.intellij.tools.ide.metrics.collector.telemetry

import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.withRetryBlocking
import it.unimi.dsi.fastutil.objects.Object2ObjectFunction
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.time.Duration.Companion.milliseconds

private val json = Json {
  ignoreUnknownKeys = true
  // parse tag value as string
  isLenient = true
}

@Serializable
private data class OpentelemetryJson(
  @JvmField val data: List<OpentelemetryJsonData> = emptyList(),
)

@Serializable
private data class OpentelemetryJsonData(
  @JvmField val traceID: String? = null,
  @JvmField val spans: List<SpanData> = emptyList(),
)

private fun getSpans(file: Path): List<SpanData> {
  val spanData = withRetryBlocking(
    messageOnFailure = "Failure during spans extraction from OpenTelemetry json file",
    retries = 5,
    printFailuresMode = PrintFailuresMode.ONLY_LAST_FAILURE,
    delay = 300.milliseconds,
  ) {
    val root = Files.newInputStream(file).use {
      json.decodeFromStream<OpentelemetryJson>(it)
    }
    val data = root.data
    check(!data.isEmpty()) {
      "No 'data' node in json at path $file"
    }
    requireNotNull(data.firstOrNull()) {
      "First data element is absent in json file $file"
    }

    data
  }

  val allSpans = spanData?.firstOrNull()?.spans
  check(!allSpans.isNullOrEmpty()) {
    "No spans was found"
  }
  return allSpans
}

private fun getParentToSpanMap(spans: List<SpanData>): Object2ObjectLinkedOpenHashMap<String, MutableSet<SpanElement>> {
  val indexParentToChild = Object2ObjectLinkedOpenHashMap<String, MutableSet<SpanElement>>()
  for (span in spans) {
    val parentSpanId = span.getParentSpanId()
    if (parentSpanId != null) {
      indexParentToChild.computeIfAbsent(parentSpanId, Object2ObjectFunction { ObjectLinkedOpenHashSet() }).add(toSpanElement(span))
    }
  }
  return indexParentToChild
}

open class OpentelemetrySpanJsonParser(private val spanFilter: SpanFilter) {
  fun getSpanElements(file: Path, spanElementFilter: Predicate<SpanElement> = Predicate { true }): Set<SpanElement> {
    val rawSpans = getSpans(file)
    val index = getParentToSpanMap(rawSpans)
    val result = ObjectLinkedOpenHashSet<SpanElement>()
    for (span in rawSpans.asSequence().filter(spanFilter.rawFilter::test).map { toSpanElement(it) }.filter { spanElementFilter.test(it) }) {
      result.add(span)
      processChild(result, span, index)
    }
    return result
  }

  protected open fun processChild(result: MutableSet<SpanElement>, parent: SpanElement, index: Map<String, Collection<SpanElement>>) {
    index.get(parent.spanId)?.forEach {
      if (parent.isWarmup) {
        it.isWarmup = true
      }
      result.add(it)
      processChild(result = result, parent = it, index = index)
    }
  }
}