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
import java.nio.file.Path

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
