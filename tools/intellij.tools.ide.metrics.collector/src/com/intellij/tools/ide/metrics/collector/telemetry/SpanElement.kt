@file:Suppress("ReplaceGetOrSet")

package com.intellij.tools.ide.metrics.collector.telemetry

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
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
    duration = span.durationNano ?: (span.duration.times(1000)),
    startTimestamp = span.startTimeNano ?: span.startTime?.let { Instant.ofEpochMilli(it / 1_000) }
                     ?: throw IllegalStateException("startTime or startTimeNano should exists"),
    spanId = span.spanID,
    parentSpanId = span.getParentSpanId(),
    tags = tags,
  )
}


/**
 * OT has a very verbose format with a lot of string duplication.
 * Using cache, we can save about 30% of memory required to parse JSON.
 */
private class CachedStringSerializer : KSerializer<String> {
  private val delegate = String.serializer()

  override val descriptor: SerialDescriptor = delegate.descriptor

  override fun serialize(encoder: Encoder, value: String) {
    delegate.serialize(encoder, value)
  }

  override fun deserialize(decoder: Decoder): String {
    val deserialized = delegate.deserialize(decoder)
    return OpenTelemetryDeserializerCache.stringCache.getOrPut(deserialized) { deserialized }
  }
}

private object CachedTagListSerializer : KSerializer<List<SpanTag>> by createCachedListSerializer(
  listCache = OpenTelemetryDeserializerCache.tagListCache,
  elementCache = OpenTelemetryDeserializerCache.tagCache
)

private object CachedReferencesListSerializer : KSerializer<List<SpanRef>> by createCachedListSerializer(
  listCache = OpenTelemetryDeserializerCache.referencesListCache,
  elementCache = OpenTelemetryDeserializerCache.referencesCache
)

private inline fun <reified T : Any> createCachedListSerializer(
  listCache: MutableMap<List<T>, List<T>>,
  elementCache: MutableMap<T, T>
): KSerializer<List<T>> {
  return object : KSerializer<List<T>> {
    private val elementSerializer = CachedElementSerializer(serializer(), elementCache)
    private val delegate = ListSerializer(elementSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) {
      delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<T> {
      val deserialized = delegate.deserialize(decoder)
      return listCache.getOrPut(deserialized) { deserialized }
    }
  }
}

private class CachedElementSerializer<T : Any>(
  private val serializer: KSerializer<T>,
  private val cache: MutableMap<T, T>
) : KSerializer<T> {

  override val descriptor: SerialDescriptor = serializer.descriptor

  override fun serialize(encoder: Encoder, value: T) {
    serializer.serialize(encoder, value)
  }

  override fun deserialize(decoder: Decoder): T {
    val deserialized = serializer.deserialize(decoder)
    return cache.getOrPut(deserialized) { deserialized }
  }
}

internal object OpenTelemetryDeserializerCache {
  val stringCache = ConcurrentHashMap<String, String>()
  val tagListCache = ConcurrentHashMap<List<SpanTag>, List<SpanTag>>()
  val tagCache = ConcurrentHashMap<SpanTag, SpanTag>()
  val referencesListCache = ConcurrentHashMap<List<SpanRef>, List<SpanRef>>()
  val referencesCache = ConcurrentHashMap<SpanRef, SpanRef>()

  fun clearCaches() {
    stringCache.clear()
    tagListCache.clear()
    referencesListCache.clear()
    tagCache.clear()
    referencesCache.clear()
  }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SpanData(
  @JvmField val spanID: String,
  @JvmField @Serializable(with = CachedStringSerializer::class) val operationName: String,

  // see com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter.export
  @Contextual val duration: Duration,
  @Contextual val durationNano: Duration? = null,
  @Contextual val startTimeNano: Instant? = null,
  @Contextual val startTime: Long? = null,

  @JvmField @Serializable(with = CachedReferencesListSerializer::class) val references: List<SpanRef> = emptyList(),
  @JvmField @Serializable(with = CachedTagListSerializer::class) val tags: List<SpanTag> = emptyList(),
)

@Serializable
data class SpanRef(
  @JvmField @Serializable(with = CachedStringSerializer::class)val refType: String? = null,
  @JvmField @Serializable(with = CachedStringSerializer::class) val spanID: String? = null,
)

@Serializable
data class SpanTag(
  @JvmField @Serializable(with = CachedStringSerializer::class) val key: String? = null,
  @JvmField @Serializable(with = CachedStringSerializer::class) val value: String? = null,
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