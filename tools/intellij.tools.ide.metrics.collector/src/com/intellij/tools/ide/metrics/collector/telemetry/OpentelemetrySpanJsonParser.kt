@file:Suppress("ReplaceGetOrSet", "OPT_IN_USAGE", "SSBasedInspection")

package com.intellij.tools.ide.metrics.collector.telemetry

import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.withRetryBlocking
import it.unimi.dsi.fastutil.objects.Object2ObjectFunction
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
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
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.time.Duration
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
    val jsonData = getSpans(file, jsonSerializerNanoseconds)

    val spans = jsonData.data.single().spans
    val index = getParentToSpanMap(spans)
    val result = ObjectLinkedOpenHashSet<SpanElement>()

    for (span in spans.filter(spanFilter.rawFilter::test).map { toSpanElement(it) }.filter { spanElementFilter(it) }) {
      result.add(span)
      processChild(result, span, index)
    }
    OpenTelemetryDeserializerCache.clearCaches()
    return result
  }

  protected open fun processChild(result: MutableSet<SpanElement>, parent: SpanElement, index: Map<String, Collection<SpanData>>) {
    index.get(parent.spanId)?.forEach {
      val span = toSpanElement(it)
      if (parent.isWarmup) {
        span.isWarmup = true
      }
      result.add(span)
      processChild(result = result, parent = span, index = index)
    }
  }

  private fun getSpans(file: Path, jsonSerializer: Json): OpentelemetryJson {
    var lastFailure: Throwable? = null
    val jsonData = withRetryBlocking(
      messageOnFailure = "Failure during spans extraction from OpenTelemetry json file $file",
      retries = 5,
      printFailuresMode = PrintFailuresMode.ONLY_LAST_FAILURE,
      delay = 300.milliseconds,
    ) {
      try {
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
      catch (e: Throwable) {
        // remember the real cause: withRetryBlocking only logs it and returns null on exhaustion
        lastFailure = e
        throw e
      }
    }

    val allSpans = jsonData?.data?.firstOrNull()?.spans
    if (allSpans.isNullOrEmpty()) {
      // surface the underlying failure (e.g. truncated json) and the file state instead of a bare NPE upstream
      throw IllegalStateException(
        "No spans were extracted from OpenTelemetry json file. ${describeTelemetryJsonFile(file)}",
        lastFailure,
      )
    }
    return jsonData
  }

  private fun getParentToSpanMap(spans: List<SpanData>): Object2ObjectOpenHashMap<String, ArrayList<SpanData>> {
    val indexParentToChild = Object2ObjectOpenHashMap<String, ArrayList<SpanData>>()
    for (span in spans) {
      val parentSpanId = span.getParentSpanId()
      if (parentSpanId != null) {
        indexParentToChild.computeIfAbsent(parentSpanId, Object2ObjectFunction { ArrayList(5) }).add(span)
      }
    }
    return indexParentToChild
  }
}

/**
 * Human-readable description of an OpenTelemetry json file's on-disk state, for failure diagnostics.
 *
 * Reports size, last-modified time, whether the document is finalized (ends with the closing `"]}]}"` that
 * [com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter] appends on flush), the name of
 * the last span near the end (which fingerprints the subsystem still writing), and the trailing bytes. That is
 * enough to tell a truncated / concurrently-written file from a missing or empty one, and by whom.
 * Reads only metadata and a small tail, so it is safe on very large files.
 */
fun describeTelemetryJsonFile(file: Path, tailBytes: Int = 4096): String {
  if (!Files.exists(file)) return "file=$file (does not exist)"
  return try {
    val size = Files.size(file)
    val lastModified = Files.getLastModifiedTime(file)
    val tail = FileChannel.open(file, StandardOpenOption.READ).use { channel ->
      val from = maxOf(0L, channel.size() - tailBytes)
      val buffer = ByteBuffer.allocate((channel.size() - from).toInt())
      channel.position(from)
      while (buffer.hasRemaining() && channel.read(buffer) >= 0) { /* read tail */ }
      String(buffer.array(), 0, buffer.position(), Charsets.UTF_8)
    }
    val finalized = tail.trimEnd().endsWith("]}]}")
    // the last span name near the end fingerprints the subsystem still writing (e.g. a live language server)
    val lastSpan = Regex("\"operationName\"\\s*:\\s*\"([^\"]*)\"").findAll(tail).lastOrNull()?.groupValues?.get(1)
    "file=$file size=$size bytes lastModified=$lastModified finalized=$finalized" +
    (lastSpan?.let { " lastSpanNearEnd=$it" } ?: "") +
    " tail=${tail.takeLast(200).replace('\n', ' ')}"
  }
  catch (e: Throwable) {
    "file=$file (failed to read diagnostics: ${e.message})"
  }
}
