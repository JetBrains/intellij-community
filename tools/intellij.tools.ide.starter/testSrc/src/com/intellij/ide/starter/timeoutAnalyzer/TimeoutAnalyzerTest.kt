package com.intellij.ide.starter.timeoutAnalyzer

import com.intellij.ide.starter.report.TimeoutAnalyzer
import com.intellij.ide.starter.runner.IDEReportingData
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.JarUtils
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class TimeoutAnalyzerTest {

  private lateinit var logsDir: Path

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var runContextMock: IDERunContext

  @BeforeEach
  fun setUp() {
    MockitoAnnotations.openMocks(this)
    Mockito.lenient().doReturn("timeout-analyzer-test").`when`(runContextMock).contextName
  }

  @TempDir
  lateinit var tempDir: Path

  private fun setUpLogsDir(resourcePath: String) {
    logsDir = JarUtils.extractResource(resourcePath, tempDir)
    val reportingData = Mockito.mock(IDEReportingData::class.java)
    Mockito.lenient().doReturn(logsDir).`when`(reportingData).logsDir
    Mockito.lenient().doReturn(reportingData).`when`(runContextMock).lastIdeReportingData
    Mockito.lenient().doReturn(listOf(reportingData)).`when`(runContextMock).registeredIdeReportingData()
    Mockito.lenient().doReturn(listOf(reportingData)).`when`(runContextMock).ideReportingDataFromCurrentToOldest()
  }

  @Test
  fun testDialogInKillThreadDump() {
    setUpLogsDir("all-data")
    val error = TimeoutAnalyzer.analyzeTimeout(runContextMock)
    error.shouldNotBeNull().messageText.shouldContain("due to a dialog being shown")
  }

  @Test
  fun testDialogInMonitoringThreadDump() {
    setUpLogsDir("no-kill-dump")
    val error = TimeoutAnalyzer.analyzeTimeout(runContextMock)
    error.shouldNotBeNull().messageText.shouldContain("due to a dialog being shown")
  }

  /**
   * A dump name carries a hash once it has been shortened to fit a path, so the name no longer says which dump came last. Both directions
   * are asserted, one dump showing a dialog and the other not: a selector that answered with the wrong file would report the wrong thing
   * one way round, and report nothing the other way round.
   */
  @Test
  fun testLatestMonitoringThreadDumpUsesModificationTime() {
    setUpLogsDir("no-kill-dump")
    val threadDumpsDirectory = logsDir.resolve("thread-dumps-ide")
    val withDialog = Files.list(threadDumpsDirectory).use { it.findFirst().orElseThrow() }
    val withoutDialog = threadDumpsDirectory.resolve("threadDump-cafe.txt")
    Files.copy(monitoringDumpWithoutDialog(), withoutDialog)

    Files.setLastModifiedTime(withDialog, FileTime.fromMillis(1))
    Files.setLastModifiedTime(withoutDialog, FileTime.fromMillis(2))
    TimeoutAnalyzer.analyzeTimeout(runContextMock).shouldBeNull()

    Files.setLastModifiedTime(withDialog, FileTime.fromMillis(3))
    TimeoutAnalyzer.analyzeTimeout(runContextMock)
      .shouldNotBeNull().messageText.shouldContain("due to a dialog being shown")
  }

  /** With one modification time between two dumps, the greater name is the one to answer with. */
  @Test
  fun testLatestMonitoringThreadDumpUsesNameWhenModificationTimesMatch() {
    setUpLogsDir("no-kill-dump")
    val threadDumpsDirectory = logsDir.resolve("thread-dumps-ide")
    val fixtureDump = Files.list(threadDumpsDirectory).use { it.findFirst().orElseThrow() }
    val dumpShowingDialog = Files.readAllBytes(fixtureDump)
    Files.delete(fixtureDump)

    val lesserName = threadDumpsDirectory.resolve("threadDump-0000.txt")
    val greaterName = threadDumpsDirectory.resolve("threadDump-ffff.txt")

    // the greater name shows no dialog, so the analyzer must report none
    writeDumpsSharingOneTime(lesserName to dumpShowingDialog, greaterName to dumpShowingNoDialog())
    TimeoutAnalyzer.analyzeTimeout(runContextMock).shouldBeNull()

    // the greater name shows the dialog, so the analyzer must report it
    writeDumpsSharingOneTime(lesserName to dumpShowingNoDialog(), greaterName to dumpShowingDialog)
    TimeoutAnalyzer.analyzeTimeout(runContextMock)
      .shouldNotBeNull().messageText.shouldContain("due to a dialog being shown")
  }

  private fun writeDumpsSharingOneTime(vararg dumps: Pair<Path, ByteArray>) {
    val sameTime = FileTime.fromMillis(1)
    dumps.forEach { (path, content) ->
      Files.write(path, content)
      Files.setLastModifiedTime(path, sameTime)
    }
  }

  @Test
  fun testLatestKillThreadDumpUsesModificationTime() {
    setUpLogsDir("all-data")
    val withDialog = Files.list(logsDir)
      .use { files -> files.filter { it.fileName.toString().startsWith("threadDump-before-kill") }.findFirst().orElseThrow() }
    val withoutDialog = logsDir.resolve("threadDump-before-kill-cafe.txt")
    Files.copy(killDumpWithoutDialog(), withoutDialog)

    Files.setLastModifiedTime(withDialog, FileTime.fromMillis(1))
    Files.setLastModifiedTime(withoutDialog, FileTime.fromMillis(2))
    TimeoutAnalyzer.analyzeTimeout(runContextMock).shouldBeNull()

    Files.setLastModifiedTime(withDialog, FileTime.fromMillis(3))
    TimeoutAnalyzer.analyzeTimeout(runContextMock)
      .shouldNotBeNull().messageText.shouldContain("due to a dialog being shown")
  }

  /**
   * The monitoring dump of the `no-dialog` fixture: an EDT thread that is not showing a dialog. Paired with a dump that is, it tells a
   * selector that answered with the right dump from one that answered with any dump at all.
   *
   * `extractResource` resolves the resource name below the temp dir, so this lands beside the fixture the test reports from.
   */
  private fun monitoringDumpWithoutDialog(): Path =
    JarUtils.extractResource("no-dialog", tempDir).resolve("thread-dumps-ide").resolve("threadDump-1-1725404956966.txt")

  /** The kill dump of the `no-dialog` fixture, for the same reason. */
  private fun killDumpWithoutDialog(): Path =
    JarUtils.extractResource("no-dialog", tempDir).resolve("threadDump-before-kill-1725405196582.txt")

  private fun dumpShowingNoDialog(): ByteArray = Files.readAllBytes(monitoringDumpWithoutDialog())

  @Test
  fun testNoDialog() {
    setUpLogsDir("no-dialog")
    val error = TimeoutAnalyzer.analyzeTimeout(runContextMock)
    error.shouldBeNull()
  }

  @Test
  fun testDetectCommandFromLog() {
    setUpLogsDir("all-data")
    val error = TimeoutAnalyzer.analyzeTimeout(runContextMock)
    error.shouldNotBeNull().messageText.shouldContain("openSettingsDialog")
  }

  /** `idea.2.log` is newer than `idea.10.log`, so the rolled logs have to be ordered by their index as a number, not as a string. */
  @Test
  fun testDetectCommandFromRolledLogWithDoubleDigitIndex() {
    setUpLogsDir("rolled-logs")
    val error = TimeoutAnalyzer.analyzeTimeout(runContextMock)
    error.shouldNotBeNull().messageText.let {
      it.shouldContain("Last executed command was: newerCommand")
      it.shouldNotContain("olderCommand")
    }
  }

  @Test
  fun testNoCommandInLog() {
    setUpLogsDir("no-command-in-log")
    val error = TimeoutAnalyzer.analyzeTimeout(runContextMock)
    error.shouldNotBeNull().messageText.shouldContain("due to a dialog being shown")
  }

  @Test
  fun testNoIdeaLog() {
    setUpLogsDir("timeouts/no-idea-log")
    val error = TimeoutAnalyzer.analyzeTimeout(runContextMock)
    error.shouldNotBeNull().messageText.shouldContain("No idea.log file present in log directory")
  }

  @Test
  fun testJavaDialog() {
    setUpLogsDir("java-dialog")
    val error = TimeoutAnalyzer.analyzeTimeout(runContextMock)
    error.shouldNotBeNull().messageText.shouldContain("due to a dialog being shown")
  }

  @Test
  fun testHungIndicators() {
    setUpLogsDir("hung-indicators")
    val error = TimeoutAnalyzer.analyzeTimeout(runContextMock).shouldNotBeNull()
    error.messageText.shouldContain("during `%exitApp true` execution because some indicators haven't finished")
    error.stackTraceContent.shouldContain("Closing attached shared indexes")
    error.stackTraceContent.shouldContain("Flushing indexes for project files")
  }

  @Test
  fun testNoHungIndicators() {
    setUpLogsDir("no-hung-indicators")
    TimeoutAnalyzer.analyzeTimeout(runContextMock).shouldBeNull()
  }

  @Test
  fun testNoEdtInThreadDump() {
    setUpLogsDir("no-edt-in-dump")
    TimeoutAnalyzer.analyzeTimeout(runContextMock).shouldBeNull()
  }
}
