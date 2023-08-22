package com.intellij.searchEverywhereMl.semantics.indices

import ai.grazie.emb.FloatTextEmbedding
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.util.io.outputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class LocalEmbeddingIndexFileManager(
  private val root: Path,
  private val dimensions: Int = DEFAULT_DIMENSIONS
) {
  private val lock = ReentrantReadWriteLock()
  private val mapper = jacksonObjectMapper()

  private val idsPath = root.resolve(IDS_FILENAME)
  private val embeddingsPath = root.resolve(EMBEDDINGS_FILENAME)

  fun loadIndex(): MutableMap<String, FloatTextEmbedding> = lock.read {
    if (!idsPath.exists() || !embeddingsPath.exists()) return mutableMapOf()
    val ids: MutableList<String> = mapper.readValue(idsPath.toFile())
    val buffer = ByteArray(EMBEDDING_ELEMENT_SIZE)
    return embeddingsPath.inputStream().use { input ->
      ids.associateWith {
        FloatTextEmbedding(FloatArray(dimensions) {
          input.read(buffer)
          ByteBuffer.wrap(buffer).getFloat()
        })
      }
    }.toMutableMap()
  }

  fun saveIndex(index: Map<String, FloatTextEmbedding>) = lock.write {
    Files.createDirectories(root)
    val (ids, embeddings) = index.toList().unzip()
    mapper.writerWithDefaultPrettyPrinter().writeValue(idsPath.toFile(), ids)
    val buffer = ByteBuffer.allocate(EMBEDDING_ELEMENT_SIZE)
    embeddingsPath.outputStream().use { output ->
      embeddings.forEach { embedding ->
        embedding.values.forEach {
          output.write(buffer.putFloat(0, it).array())
        }
      }
    }
  }

  companion object {
    private const val IDS_FILENAME = "ids.json"
    private const val EMBEDDINGS_FILENAME = "embeddings.bin"
    private const val DEFAULT_DIMENSIONS = 384
    private const val EMBEDDING_ELEMENT_SIZE = 4
  }
}
