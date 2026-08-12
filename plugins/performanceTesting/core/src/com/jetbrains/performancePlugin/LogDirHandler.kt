// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.idea.IdeaLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PathManager.PROPERTY_LOG_PATH
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.AttachmentHandler
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.JulLogger
import com.intellij.openapi.diagnostic.RollingFileHandler
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger.getLogger
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

/** Caps buffering if a log switch stalls. */
private const val MAX_BUFFERED_RECORDS = 200_000

@Service(Service.Level.APP)
class LogDirHandler : Disposable {
  companion object {
    /** Returns the active log directory, including runtime switches that [PathManager.getLogDir] does not follow. */
    @JvmStatic
    fun currentLogDir(): Path =
      System.getProperty(PathManager.PROPERTY_LOG_PATH)
        ?.takeIf { it.isNotBlank() }
        ?.let { Path.of(it).toAbsolutePath().normalize() }
      ?: PathManager.getLogDir()
  }

  /** Detached handlers stay open until the next switch so late publishers do not lose records. */
  private var handlersOfThePreviousLogDir: List<RollingFileHandler> = emptyList()

  /**
   * Switches a reused IDE process to a fresh log directory without dropping concurrent log records.
   * `PerformanceWatcher` freeze reports and thread dumps, OpenTelemetry metrics, and `DetailedEventWatcher`'s `edt-log` still use
   * [PathManager.getLogDir] and remain in the startup directory.
   */
  fun setLogDir(logDir: String) {
    logger<LogDirHandler>().info("Setting new current log dir: $logDir")
    val logDirFullPath = Path.of(logDir).toAbsolutePath().normalize()
    val newLogFile = logDirFullPath.resolve("idea.log")
    val onRotate = Runnable(IdeaLogger::dropFrequentExceptionsCaches)
    val rootLogger = getLogger("")
    val recordsDropped = synchronized(rootLogger) {
      // Close handlers retained by the preceding switch before rolling their files.
      handlersOfThePreviousLogDir.forEach { it.close() }
      handlersOfThePreviousLogDir = emptyList()

      // Buffer during replacement; racing records may be duplicated but are not lost.
      val transitionBuffer = TransitionBufferHandler()
      rootLogger.addHandler(transitionBuffer)
      try {
        val detachedHandlers = detachCurrentHandlers(rootLogger)
        // Close the target-file handler before rolling; retain the others for late publishers.
        val (writingTheLogToRoll, writingElsewhere) = detachedHandlers.fileHandlers.partition { it.logPath == newLogFile }
        writingTheLogToRoll.forEach { it.close() }
        handlersOfThePreviousLogDir = writingElsewhere

        onRotate.run()
        rollExistingLog(newLogFile)

        val newHandlers = createHandlers(newLogFile, detachedHandlers.hadAttachmentHandler, onRotate)
        newHandlers.forEach(rootLogger::addHandler)
        transitionBuffer.drainAndForwardTo(newHandlers)
      }
      finally {
        rootLogger.removeHandler(transitionBuffer)
      }
    }
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking { sweepExistingErrors() }
    System.setProperty(PROPERTY_LOG_PATH, logDirFullPath.toString())
    if (recordsDropped > 0) {
      logger<LogDirHandler>().error(
        "$recordsDropped log records logged while the log dir was being switched did not fit into" +
        " the $MAX_BUFFERED_RECORDS records of the transition buffer and were lost"
      )
    }
    logger<LogDirHandler>().info("Switched IDE log dir to: $logDir")
  }

  private fun detachCurrentHandlers(rootLogger: java.util.logging.Logger): DetachedHandlers {
    val fileHandlers = rootLogger.handlers.filterIsInstance<RollingFileHandler>()
    val attachmentHandlers = rootLogger.handlers.filterIsInstance<AttachmentHandler>()
    fileHandlers.forEach { handler ->
      handler.flush()
      rootLogger.removeHandler(handler)
    }
    attachmentHandlers.forEach(rootLogger::removeHandler)
    return DetachedHandlers(fileHandlers, attachmentHandlers.isNotEmpty())
  }

  private fun createHandlers(logFile: Path, withAttachmentHandler: Boolean, onRotate: Runnable): List<Handler> {
    val fileHandler = RollingFileHandler(
      logPath = logFile,
      limit = JulLogger.LOG_FILE_SIZE_LIMIT,
      count = JulLogger.LOG_FILE_COUNT,
      append = false,
      onRotate = onRotate,
    ).apply {
      formatter = IdeaLogRecordFormatter()
      level = Level.FINEST
    }
    return buildList {
      add(fileHandler)
      if (withAttachmentHandler) add(AttachmentHandler(logFile))
    }
  }

  /** Preserves an existing target log before the new non-appending handler opens it. */
  private fun rollExistingLog(logFile: Path) {
    if (!logFile.exists()) return

    val count = JulLogger.LOG_FILE_COUNT
    try {
      Files.deleteIfExists(logFileWithIndex(logFile, count))
      for (index in count - 1 downTo 1) {
        val rolled = logFileWithIndex(logFile, index)
        if (rolled.exists()) {
          Files.move(rolled, logFileWithIndex(logFile, index + 1), StandardCopyOption.ATOMIC_MOVE)
        }
      }
      Files.move(logFile, logFileWithIndex(logFile, 1), StandardCopyOption.ATOMIC_MOVE)
    }
    catch (e: IOException) {
      // Fall back to the previous overwrite behavior if rolling fails.
      logger<LogDirHandler>().warn("Failed to roll $logFile, its content will be overwritten", e)
    }
  }

  private fun logFileWithIndex(logFile: Path, index: Int): Path =
    logFile.resolveSibling("${logFile.nameWithoutExtension}.$index.${logFile.extension}")

  override fun dispose() {
    synchronized(java.util.logging.Logger.getLogger("")) {
      handlersOfThePreviousLogDir.forEach { it.close() }
      handlersOfThePreviousLogDir = emptyList()
    }
  }
}

private data class DetachedHandlers(
  val fileHandlers: List<RollingFileHandler>,
  val hadAttachmentHandler: Boolean,
)

/**
 * Buffers records while file handlers are replaced, then forwards late publications.
 * A handoff race can duplicate a record but does not lose it.
 */
@VisibleForTesting
internal class TransitionBufferHandler : Handler() {
  private val records = ArrayList<LogRecord>()
  private var newHandlers: List<Handler>? = null
  private var dropped = 0

  override fun publish(record: LogRecord) {
    synchronized(records) {
      val handlers = newHandlers
      when {
        handlers != null -> handlers.forEach { it.publish(record) }
        records.size < MAX_BUFFERED_RECORDS -> records.add(record)
        else -> dropped++
      }
    }
  }

  /** Replays buffered records, forwards later records, and returns the overflow count. */
  fun drainAndForwardTo(newHandlers: List<Handler>): Int {
    synchronized(records) {
      // Set forwarding first because publishing an attachment can log reentrantly.
      this.newHandlers = newHandlers
      records.forEach { record -> newHandlers.forEach { it.publish(record) } }
      records.clear()
      return dropped
    }
  }

  override fun flush() {}

  override fun close() {}
}
