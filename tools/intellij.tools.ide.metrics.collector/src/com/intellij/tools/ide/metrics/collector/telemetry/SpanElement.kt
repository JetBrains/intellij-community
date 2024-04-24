@file:Suppress("ReplaceGetOrSet")

package com.intellij.tools.ide.metrics.collector.telemetry

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/**
 * Represents a span element that defines a unit of work or a segment of time.
 *
 * @property isWarmup Indicates whether this span element is a warm-up span.
 * @property name The name of the span element.
 * @property duration The duration of the span element in milliseconds.
 * @property startTimestamp The timestamp when the span element started, in milliseconds.
 * @property spanId The unique identifier for the span element.
 * @property parentSpanId The unique identifier of the parent span element, if any.
 * @property tags The list of key-value pairs representing the tags associated with the span element.
 */
data class SpanElement(
  @JvmField var isWarmup: Boolean,
  @JvmField val name: String,
  @JvmField val duration: Long,
  @JvmField val startTimestamp: Long,
  @JvmField val spanId: String,
  @JvmField val parentSpanId: String?,
  @JvmField val tags: List<Pair<String, String>>,
)

val SpanElement.finishTimestamp: Long
  get() = startTimestamp + duration

internal fun toSpanElement(span: SpanData): SpanElement {
  val tags = getTags(span)
  return SpanElement(
    isWarmup = isWarmup(tags),
    name = span.operationName,
    duration = (span.duration / 1000.0).roundToLong(),
    startTimestamp = (span.startTime / 1000.0).roundToLong(),
    spanId = span.spanID,
    parentSpanId = span.getParentSpanId(),
    tags = tags,
  )
}

@Serializable
data class SpanData(
  @JvmField val spanID: String,
  @JvmField val operationName: String,
  @JvmField val duration: Long,
  @JvmField val startTime: Long,
  @JvmField val references: List<SpanRef> = emptyList(),
  @JvmField val tags: List<SpanTag> = emptyList(),
)

@Serializable
data class SpanRef(
  @JvmField val refType: String? = null,
  @JvmField val traceID: String? = null,
  @JvmField val spanID: String? = null,
)

@Serializable
data class SpanTag(
  @JvmField val key: String? = null,
  @JvmField val type: String? = null,
  @JvmField val value: String? = null,
)

internal fun SpanData.getParentSpanId(): String? {
  return references.firstOrNull { it.refType == "CHILD_OF" }?.spanID
}

private fun getTags(span: SpanData): List<Pair<String, String>> {
  val tags = ArrayList<Pair<String, String>>(span.tags.size)
  for (tag in span.tags) {
    val attributeName = tag.key!!
    val textValue = tag.value!!
    tags.add(Pair(attributeName, textValue))
  }
  return tags
}

private fun isWarmup(tags: List<Pair<String, String>>): Boolean {
  return tags.find { it.first == "warmup" && it.second == "true" } != null
}