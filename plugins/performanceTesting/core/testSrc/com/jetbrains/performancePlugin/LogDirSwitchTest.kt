// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.AttachmentHandler
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.JulLogger
import com.intellij.openapi.diagnostic.RollingFileHandler
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.util.Disposer
import com.jetbrains.performancePlugin.commands.MemoryDumpCommand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.system.measureTimeMillis

private const val PROBE_PREFIX = "log-dir-switch-probe-"
private val PROBE: Regex = Regex("$PROBE_PREFIX(\\d+)")

/**
 * How many times [LogDirSwitchTest.noLinesAreLostWhileTheDirIsBeingSwitched] switches the dir. Kept below [JulLogger.LOG_FILE_COUNT]
 * per dir, so that no log holding probe lines is rolled out of the set the switches keep.
 */
private const val SWITCHES = 10

/**
 * Drives [LogDirHandler.setLogDir] against the real JUL root logger and real [RollingFileHandler]s, the way a driver test that reuses
 * one IDE process for several test methods does, and looks at what actually ends up in the log files.
 *
 * The root logger is process-wide, so the handlers of the test run itself are taken aside for the duration of every test:
 * [LogDirHandler.setLogDir] takes over every [RollingFileHandler] it finds on the root logger, and taking over the one of the test run
 * would leave the rest of the run without a log.
 */
internal class LogDirSwitchTest {
  @get:Rule
  val tempFolder: TemporaryFolder = TemporaryFolder()

  private val rootLogger = java.util.logging.Logger.getLogger("")

  /** A logger of its own, so that the probe lines are not filtered out by whatever levels the test run configures. */
  private val probeLogger = java.util.logging.Logger.getLogger("${LogDirSwitchTest::class.java.name}.probe")

  private lateinit var handlersOfThisTestRun: List<Handler>
  private var logPathOfThisTestRun: String? = null

  private lateinit var firstMethodLogDir: Path
  private lateinit var secondMethodLogDir: Path

  /** One for the whole test, the way a running IDE has one application service of it. */
  private val logDirSetter = LogDirHandler()

  @Before
  fun startAsAnIdeThatAlreadyWritesALog() {
    handlersOfThisTestRun = rootLogger.handlers.toList()
    handlersOfThisTestRun.forEach { rootLogger.removeHandler(it) }  // detached, deliberately not closed
    logPathOfThisTestRun = System.getProperty(PathManager.PROPERTY_LOG_PATH)

    firstMethodLogDir = tempFolder.newFolder("log-of-the-first-method").toPath()
    secondMethodLogDir = tempFolder.newFolder("log-of-the-second-method").toPath()

    probeLogger.level = Level.ALL
    rootLogger.addHandler(rollingFileHandler(firstMethodLogDir.resolve("idea.log")))
    System.setProperty(PathManager.PROPERTY_LOG_PATH, firstMethodLogDir.toString())
  }

  @After
  fun restoreTheLoggingOfThisTestRun() {
    probeLogger.level = null
    Disposer.dispose(logDirSetter)  // closes the log of the dir the last switch left, which no further switch is going to close
    rootLogger.handlers.forEach {
      rootLogger.removeHandler(it)
      it.close()
    }
    handlersOfThisTestRun.forEach { rootLogger.addHandler(it) }

    val original = logPathOfThisTestRun
    if (original == null) System.clearProperty(PathManager.PROPERTY_LOG_PATH)
    else System.setProperty(PathManager.PROPERTY_LOG_PATH, original)
  }

  /** Nothing logs while the dir is being switched: every line lands in the dir of the method that logged it. */
  @Test
  fun linesLoggedAroundTheSwitchGoToTheDirOfTheirOwnMethod() {
    probeLogger.info(probe(1))
    logDirSetter.setLogDir(secondMethodLogDir.toString())
    probeLogger.info(probe(2))
    flushCurrentHandlers()

    assertEquals(listOf(1), probesIn(firstMethodLogDir.resolve("idea.log")))
    assertEquals(listOf(2), probesIn(secondMethodLogDir.resolve("idea.log")))
  }

  @Test
  fun attachmentsFollowTheLogDirSwitch() {
    rootLogger.addHandler(AttachmentHandler(firstMethodLogDir.resolve("idea.log")))

    logAttachment("first.txt")
    logDirSetter.setLogDir(secondMethodLogDir.toString())
    logAttachment("second.txt")

    assertEquals(listOf("first.txt"), attachmentFileNamesIn(firstMethodLogDir))
    assertEquals(listOf("second.txt"), attachmentFileNamesIn(secondMethodLogDir))
  }

  @Test
  fun memoryDumpsFollowTheLogDirSwitch() {
    logDirSetter.setLogDir(secondMethodLogDir.toString())

    assertEquals(secondMethodLogDir, MemoryDumpCommand.getMemoryDumpPath().parent)
  }

  @Test
  fun artifactsUseTheOriginalLogDirBeforeTheFirstSwitch() {
    assertEquals(PathManager.getOriginalLogDir(), LogDirHandler.currentLogDir())
  }

  @Test
  fun recordsLoggedReentrantlyWhileTheTransitionBufferIsDrainedAreForwarded() {
    val transitionBuffer = TransitionBufferHandler()
    val forwardedMessages = mutableListOf<String>()
    val reentrantHandler = object : Handler() {
      override fun publish(record: LogRecord) {
        forwardedMessages.add(record.message)
        if (record.message == "attachment") {
          transitionBuffer.publish(LogRecord(Level.INFO, "saving attachment"))
        }
      }

      override fun flush() {}

      override fun close() {}
    }
    transitionBuffer.publish(LogRecord(Level.SEVERE, "attachment"))

    transitionBuffer.drainAndForwardTo(listOf(reentrantHandler))

    assertEquals(listOf("attachment", "saving attachment"), forwardedMessages)
  }

  /**
   * A method that the IDE process is switched back to reports into the dir it already has, and a rerun can land in a test home kept from
   * a previous run, so the dir switched into is not necessarily empty. The new handler is opened with `append = false`, so a log that is
   * already there has to be rolled out of the way first, under a name the starter collects anyway.
   */
  @Test
  fun switchingToAnAlreadyUsedDirRollsItsLogInsteadOfTruncatingIt() {
    probeLogger.info(probe(1))
    logDirSetter.setLogDir(firstMethodLogDir.toString())  // the very dir the current handler writes to
    probeLogger.info(probe(2))
    flushCurrentHandlers()

    assertEquals(listOf(2), probesIn(firstMethodLogDir.resolve("idea.log")))
    assertEquals(listOf(1), probesIn(firstMethodLogDir.resolve("idea.1.log")))
  }

  /**
   * [LogDirHandler.setLogDir] detaches the old handler, rolls the log of the target dir and only then attaches the new one, so for as
   * long as that takes the root logger has no file handler at all - and `synchronized` on it does not help, because
   * `java.util.logging.Logger.log` reads the handlers of the logger and then publishes to them without holding that monitor. Without the
   * transition buffer that captures those records, everything another thread logged in that window used to be dropped on the floor.
   *
   * The dir is switched back and forth [SWITCHES] times rather than once, because what is left to go wrong at the two ends of the
   * window is a hairline between reading the handlers and publishing to them: a single switch passes even when it is still there.
   *
   * The second dir is seeded with a full set of rolled logs on purpose: that is the realistic worst case (a rerun into a preserved test
   * home), and the rolling it forces widens the window enough to make the test meaningful rather than lucky. From the second switch on,
   * both dirs have logs to roll anyway.
   */
  @Test
  fun noLinesAreLostWhileTheDirIsBeingSwitched() {
    seedRolledLogs(secondMethodLogDir)

    val logged = AtomicInteger()
    val stopLogging = AtomicBoolean()
    val writer = thread(name = "log-dir-switch-probe-writer") {
      while (!stopLogging.get()) probeLogger.info(probe(logged.incrementAndGet()))
    }
    var switchesTookMs = 0L
    try {
      while (logged.get() < 10) Thread.onSpinWait()  // let the writer reach a steady rate before the first switch
      repeat(SWITCHES) { switch ->
        val target = if (switch % 2 == 0) secondMethodLogDir else firstMethodLogDir
        switchesTookMs += measureTimeMillis { logDirSetter.setLogDir(target.toString()) }
        val loggedRightAfterTheSwitch = logged.get()
        while (logged.get() < loggedRightAfterTheSwitch + 10) Thread.onSpinWait()  // and keep writing for a while after it
      }
    }
    finally {
      stopLogging.set(true)
      writer.join()
    }
    flushCurrentHandlers()

    val written = probesIn(*(logsIn(firstMethodLogDir) + logsIn(secondMethodLogDir)).toTypedArray())
    val lost = (1..logged.get()) - written.toSet()
    assertEquals(
      "${lost.size} of the ${logged.get()} lines logged by another thread were lost over $SWITCHES log dir switches" +
      (if (lost.isEmpty()) "" else ", lines ${lost.first()}..${lost.last()}") +
      ", which together took $switchesTookMs ms. They are in none of the logs of either dir.",
      0, lost.size
    )
  }

  private fun rollingFileHandler(logFile: Path): RollingFileHandler =
    RollingFileHandler(logPath = logFile, limit = JulLogger.LOG_FILE_SIZE_LIMIT, count = JulLogger.LOG_FILE_COUNT, append = false)
      .apply {
        formatter = IdeaLogRecordFormatter()
        level = Level.FINEST
      }

  private fun flushCurrentHandlers() = rootLogger.handlers.forEach { it.flush() }

  private fun logAttachment(name: String) {
    probeLogger.log(Level.SEVERE, name, RuntimeExceptionWithAttachments(name, Attachment(name, name)))
  }

  private fun attachmentFileNamesIn(logDir: Path): List<String> {
    val attachmentsDir = logDir.resolve("attachments")
    if (!attachmentsDir.exists()) return emptyList()
    return Files.walk(attachmentsDir).use { paths ->
      paths.filter { Files.isRegularFile(it) }
        .map { it.fileName.toString() }
        .filter { it != "stacktrace.txt" }
        .sorted()
        .toList()
    }
  }

  /** Every log of [logDir]: the `idea.log` being written right now and the `idea.N.log` the previous switches rolled it into. */
  private fun logsIn(logDir: Path): List<Path> = logDir.listDirectoryEntries("idea*.log")

  private fun probe(index: Int): String = "$PROBE_PREFIX$index"

  private fun probesIn(vararg logFiles: Path): List<Int> =
    logFiles.filter { it.exists() }
      .flatMap { Files.readAllLines(it) }
      .mapNotNull { line -> PROBE.find(line)?.groupValues?.get(1)?.toIntOrNull() }

  /** Fills [logDir] with a full set of rolled logs, the way a previous run of the same test method would have left it. */
  private fun seedRolledLogs(logDir: Path) {
    Files.writeString(logDir.resolve("idea.log"), "a log left over by a previous run\n")
    for (index in 1 until JulLogger.LOG_FILE_COUNT) {
      Files.writeString(logDir.resolve("idea.$index.log"), "a rolled log left over by a previous run\n")
    }
  }
}
