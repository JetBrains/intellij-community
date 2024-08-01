@file:Suppress("ReplaceGetOrSet", "OPT_IN_USAGE", "SSBasedInspection")

package com.intellij.tools.ide.metrics.collector.telemetry

import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.withRetryBlocking
import it.unimi.dsi.fastutil.objects.Object2ObjectFunction
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

private const val nanoPrecision = 1_000_000_000

object DurationNanosecondsSerializer : KSerializer<Duration> {
  override val descriptor = PrimitiveSerialDescriptor("DurationNanosecondsSerializer", PrimitiveKind.LONG)

  override fun serialize(encoder: Encoder, value: Duration) {
    encoder.encodeLong(value.inWholeNanoseconds)
  }

  override fun deserialize(decoder: Decoder): Duration {
    return decoder.decodeLong().nanoseconds
  }
}

object InstantNanosecondsSerializer : KSerializer<Instant> {
  override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

  override fun serialize(encoder: Encoder, value: Instant) {
    encoder.encodeLong(value.epochSecond * nanoPrecision + value.nano)
  }

  override fun deserialize(decoder: Decoder): Instant {
    val timeStamp = decoder.decodeLong()

    return Instant.ofEpochSecond(
      timeStamp / nanoPrecision,
      timeStamp % nanoPrecision
    )
  }
}

private val jsonSerializerNanoseconds = Json {
  serializersModule = SerializersModule {
    contextual(InstantNanosecondsSerializer)
    contextual(DurationNanosecondsSerializer)
  }

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

open class OpentelemetrySpanJsonParser(private val spanFilter: SpanFilter) {
  fun getSpanElements(file: Path, spanElementFilter: (SpanElement) -> Boolean = { true }): Set<SpanElement> {
    var jsonData = getSpans(file, jsonSerializerNanoseconds)

    val spans = jsonData.data.single().spans
    val index = getParentToSpanMap(spans)
    val result = ObjectLinkedOpenHashSet<SpanElement>()

    for (span in spans.filter(spanFilter.rawFilter::test).map { toSpanElement(it) }.filter { spanElementFilter(it) }) {
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

  private fun getSpans(file: Path, jsonSerializer: Json): OpentelemetryJson {
    val jsonData = withRetryBlocking(
      messageOnFailure = "Failure during spans extraction from OpenTelemetry json file $file",
      retries = 5,
      printFailuresMode = PrintFailuresMode.ONLY_LAST_FAILURE,
      delay = 300.milliseconds,
    ) {
      val root = Files.newInputStream(file).use {
        jsonSerializer.decodeFromStream<OpentelemetryJson>(it)
      }
      val data = root.data
      check(!data.isEmpty()) {
        "No 'data' node in json at path $file"
      }
      requireNotNull(data.firstOrNull()) {
        "First data element is absent in json file $file"
      }

      root
    }

    val allSpans = jsonData?.data?.firstOrNull()?.spans
    check(!allSpans.isNullOrEmpty()) {
      "No spans were found"
    }
    return jsonData
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
}