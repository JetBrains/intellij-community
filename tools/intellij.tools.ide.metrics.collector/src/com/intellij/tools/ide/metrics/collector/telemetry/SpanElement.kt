package com.intellij.tools.ide.metrics.collector.telemetry

import com.fasterxml.jackson.databind.JsonNode
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
data class SpanElement(var isWarmup: Boolean,
                       val name: String,
                       val duration: Long,
                       val startTimestamp: Long,
                       val spanId: String,
                       val parentSpanId: String?,
                       val tags: List<Pair<String, String>>)

fun JsonNode.toSpanElement(): SpanElement {
  val spanName = this.spanName()
  val duration = getDuration(this)
  val startTimestamp = getStartTime(this)
  val tags = getTags(this)
  val isWarmup = isWarmup(tags)
  val spanId = this.get("spanID").textValue()
  val parentSpanId = this.getParentSpanId()
  return SpanElement(isWarmup, spanName, duration, startTimestamp, spanId, parentSpanId, tags)
}

fun JsonNode.spanName(): String {
  return this.get("operationName").textValue()
}

fun JsonNode.getParentSpanId(): String? {
  this.get("references")?.forEach { reference ->
    if (reference.get("refType")?.textValue() == "CHILD_OF") {
      val spanId = reference.get("spanID").textValue()
      return spanId
    }
  }
  return null
}

private fun getTags(span: JsonNode): List<Pair<String, String>> {
  val tags = mutableListOf<Pair<String, String>>()
  span.get("tags")?.forEach { tag ->
    val attributeName = tag.get("key").textValue()
    val textValue = tag.get("value").textValue()
    tags.add(Pair(attributeName, textValue))
  }
  return tags
}

private fun getDuration(span: JsonNode) = (span.get("duration").longValue() / 1000.0).roundToLong()

private fun getStartTime(span: JsonNode) = (span.get("startTime").longValue() / 1000.0).roundToLong()

private fun isWarmup(tags: List<Pair<String, String>>): Boolean {
  return tags.find { it.first == "warmup" && it.second == "true" } != null
}