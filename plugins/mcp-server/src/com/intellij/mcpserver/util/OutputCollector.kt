package com.intellij.mcpserver.util

import com.intellij.mcpserver.toolsets.Constants
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val logger = logger<OutputCollector>()

internal class OutputCollector(
  val parentScope: CoroutineScope,
  val outputPath: Path,
  val writeChunkDebouncePeriod: Duration = 50.milliseconds,
  val writeAttemptsOnError: Int = 3,
) {
  private val chunkChannel = Channel<String>(capacity = Channel.UNLIMITED)
  private val outputPreview = StringBuilder()
  @Volatile
  private var outputPreviewTruncated = false
  private val outputPreviewMutex = Mutex()
  private val writeErrorFlow = MutableStateFlow<Throwable?>(null)
  private val drainJob: Job

  init {
    require(writeAttemptsOnError > 0) { "writeAttemptsOnError must be positive" }
    drainJob = parentScope.launch {
      val chunkBatchMutex = Mutex()
      val chunkBatch = StringBuilder()
      suspend fun appendToBatch(chunk: String) {
        outputPreviewMutex.withLock {
          if (outputPreview.length < Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH) {
            outputPreview.append(chunk)
            if (outputPreview.length >= Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH) {
              outputPreview.setLength(Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH)
              // preview becomes longer than max length for the marker length. that's ok, next iteration won't append
              outputPreview.append(Constants.RUN_CONFIGURATION_PREVIEW_TRUNCATED_MARKER)
              outputPreviewTruncated = true
            }
          }
        }
        chunkBatchMutex.withLock {
          chunkBatch.append(chunk)
        }
      }
      suspend fun flush() {
        chunkBatchMutex.withLock {
          if (chunkBatch.isEmpty()) return
          writeToFileRetrying(attempts = writeAttemptsOnError, text = chunkBatch.toString())
          chunkBatch.clear()
        }
      }

      val timeoutFlushJob = launch {
        while (isActive) {
          flush()
          delay(writeChunkDebouncePeriod)
        }
      }

      for (chunk in chunkChannel) {
        appendToBatch(chunk)
      }
      timeoutFlushJob.cancel()
      flush()
    }
  }

  private suspend fun writeToFileRetrying(attempts: Int, text: String) {
    if (writeErrorFlow.value != null) return
    var lastError: Throwable? = null
    for (i in 1..attempts) {
      try {
        writeToFile(text)
        return
      } catch (e: IOException) {
        logger.trace { "Failed to write to file: ${e.message}" }
        delay(writeChunkDebouncePeriod)
        lastError = e
      }
    }
    logger.warn("Failed to write to file $outputPath after $attempts attempts. Last error logged.", lastError)
    writeErrorFlow.value = lastError
  }

  private suspend fun writeToFile(text: String) {
    withContext(Dispatchers.IO) {
      Files.newBufferedWriter(outputPath,
                              StandardCharsets.UTF_8,
                              StandardOpenOption.CREATE,
                              StandardOpenOption.WRITE,
                              StandardOpenOption.APPEND).use { writer ->
        writer.write(text)
      }
    }
  }

  fun append(text: String): Boolean {
    return chunkChannel.trySend(text).isSuccess
  }

  /**
   * Preview truncated to the [Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH]
   * but [Constants.RUN_CONFIGURATION_PREVIEW_TRUNCATED_MARKER] is appended to this max length.
   */
  suspend fun getOutputPreview(): String {
    return outputPreviewMutex.withLock {
      outputPreview.toString()
    }
  }

  val isOutputPreviewTruncated: Boolean
    get() = outputPreviewTruncated

  fun close() {
    chunkChannel.close()
  }

  suspend fun waitForDrain() {
    drainJob.join()
  }

  // TODO: pass to the result later
  val writeError: Throwable? get() = writeErrorFlow.value

  fun dispose() {
    deleteOutputFile()
  }
  private fun deleteOutputFile() {
    try {
      Files.deleteIfExists(outputPath)
    }
    catch (e: Exception) {
        rethrowControlFlowException(e)
      logger.trace { "Failed to delete temp output file on application dispose: ${e.message}" }
    }
  }
}
