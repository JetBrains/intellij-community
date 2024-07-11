@file:Suppress("ReplaceGetOrSet")

package com.intellij.tools.ide.metrics.collector.telemetry

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.time.Instant
import kotlin.time.Duration

/**
 * Represents a span element that defines a unit of work or a segment of time.
 *
 * @property isWarmup Indicates whether this span element is a warm-up span.
 * @property name The name of the span element.
 * @property duration The duration of the span element.
 * @property startTimestamp The timestamp when the span element started (nanosecond precision).
 * @property spanId The unique identifier for the span element.
 * @property parentSpanId The unique identifier of the parent span element, if any.
 * @property tags The list of key-value pairs representing the tags associated with the span element.
 */
data class SpanElement(
  @JvmField var isWarmup: Boolean,
  @JvmField val name: String,
  @Contextual val duration: Duration,
  @JvmField val startTimestamp: Instant,
  @JvmField val spanId: String,
  @JvmField val parentSpanId: String?,
  @JvmField val tags: List<Pair<String, String>>,
)

val SpanElement.finishTimestamp: Instant
  get() = startTimestamp.plusNanos(duration.inWholeNanoseconds)

internal fun toSpanElement(span: SpanData): SpanElement {
  val tags = getTags(span)
  return SpanElement(
    isWarmup = isWarmup(tags),
    name = span.operationName,
    duration = span.durationNano,
    startTimestamp = span.startTimeNano,
    spanId = span.spanID,
    parentSpanId = span.getParentSpanId(),
    tags = tags,
  )
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SpanData(
  @JvmField val spanID: String,
  @JvmField val operationName: String,

  // see com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter.export
  //@Serializable(with = DurationSerializer::class)
  @JsonNames("duration")
  @Contextual val durationNano: Duration,
  //@Serializable(with = InstantSerializer::class)
  @JsonNames("startTime")
  @Contextual val startTimeNano: Instant,

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