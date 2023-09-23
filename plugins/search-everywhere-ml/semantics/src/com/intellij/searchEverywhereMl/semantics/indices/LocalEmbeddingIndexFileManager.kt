package com.intellij.searchEverywhereMl.semantics.indices

import ai.grazie.emb.FloatTextEmbedding
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.util.io.outputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class LocalEmbeddingIndexFileManager(root: Path, private val dimensions: Int = DEFAULT_DIMENSIONS) {
  private val lock = ReentrantReadWriteLock()
  private val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

  private val prettyPrinter = DefaultPrettyPrinter().apply { indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE) }

  private val rootPath = root
    get() = field.also { Files.createDirectories(field) }
  private val idsPath
    get() = rootPath.resolve(IDS_FILENAME)
  private val embeddingsPath
    get() = rootPath.resolve(EMBEDDINGS_FILENAME)

  val embeddingSizeInBytes = dimensions * EMBEDDING_ELEMENT_SIZE

  /** Provides reading access to the embedding vector at the specified index
   *  without reading the whole file into memory
   */
  operator fun get(index: Int): FloatTextEmbedding = lock.read {
    RandomAccessFile(embeddingsPath.toFile(), "r").use { input ->
      input.seek(getIndexOffset(index))
      val buffer = ByteArray(EMBEDDING_ELEMENT_SIZE)
      FloatTextEmbedding(FloatArray(dimensions) {
        input.read(buffer)
        ByteBuffer.wrap(buffer).getFloat()
      })
    }
  }

  /** Provides writing access to embedding vector at the specified index
   *  without writing the other vectors
   */
  operator fun set(index: Int, embedding: FloatTextEmbedding) = lock.write {
    RandomAccessFile(embeddingsPath.toFile(), "rw").use { output ->
      output.seek(getIndexOffset(index))
      val buffer = ByteBuffer.allocate(EMBEDDING_ELEMENT_SIZE)
      embedding.values.forEach {
        output.write(buffer.putFloat(0, it).array())
      }
    }
  }

  /**
   * Removes the embedding vector at the specified index.
   * To do so, replaces this vector with the last vector in the file and shrinks the file size.
   */
  fun removeAtIndex(index: Int) = lock.write {
    RandomAccessFile(embeddingsPath.toFile(), "rw").use { file ->
      if (file.length() < embeddingSizeInBytes) return
      if (file.length() - embeddingSizeInBytes != getIndexOffset(index)) {
        file.seek(file.length() - embeddingSizeInBytes)
        val array = ByteArray(EMBEDDING_ELEMENT_SIZE)
        val embedding = FloatTextEmbedding(FloatArray(dimensions) {
          file.read(array)
          ByteBuffer.wrap(array).getFloat()
        })
        file.seek(getIndexOffset(index))
        val buffer = ByteBuffer.allocate(EMBEDDING_ELEMENT_SIZE)
        embedding.values.forEach {
          file.write(buffer.putFloat(0, it).array())
        }
      }
      file.setLength(file.length() - embeddingSizeInBytes)
    }
  }

  fun loadIndex(): Pair<List<String>, List<FloatTextEmbedding>>? = lock.read {
    if (!idsPath.exists() || !embeddingsPath.exists()) return null
    val ids = mapper.readValue<List<String>>(idsPath.toFile()).map { it.intern() }.toMutableList()
    val buffer = ByteArray(EMBEDDING_ELEMENT_SIZE)
    return embeddingsPath.inputStream().use { input ->
      ids to ids.map {
        FloatTextEmbedding(FloatArray(dimensions) {
          input.read(buffer)
          ByteBuffer.wrap(buffer).getFloat()
        })
      }
    }
  }

  fun saveIds(ids: List<String>) = lock.write {
    idsPath.outputStream(options = arrayOf(StandardOpenOption.SYNC)).use { output ->
      mapper.writer(prettyPrinter).writeValue(output, ids)
    }
  }

  fun saveIndex(ids: List<String>, embeddings: List<FloatTextEmbedding>) = lock.write {
    idsPath.outputStream(options = arrayOf(StandardOpenOption.SYNC)).use { output ->
      mapper.writer(prettyPrinter).writeValue(output, ids)
    }
    val buffer = ByteBuffer.allocate(EMBEDDING_ELEMENT_SIZE)
    embeddingsPath.outputStream(options = arrayOf(StandardOpenOption.SYNC)).use { output ->
      embeddings.forEach { embedding ->
        embedding.values.forEach {
          output.write(buffer.putFloat(0, it).array())
        }
      }
    }
  }

  private fun getIndexOffset(index: Int): Long = index.toLong() * embeddingSizeInBytes

  companion object {
    const val DEFAULT_DIMENSIONS = 128
    const val EMBEDDING_ELEMENT_SIZE = 4

    private const val IDS_FILENAME = "ids.json"
    private const val EMBEDDINGS_FILENAME = "embeddings.bin"
  }
}
