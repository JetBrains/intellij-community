// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.Printable
import com.intellij.execution.testframework.Printer
import com.intellij.execution.testframework.export.TestResultsXmlFormatter
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.history.ImportedToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.runInEdtAndGet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * IDEA-395942: the test history keeps the output of a test run that the IDE delegates to Maven, as Maven printed it.
 *
 * [TestResultsXmlFormatter] writes the output of the root node only when the root has a name. In a delegated run, the
 * Maven output is on the root node, so the run must give the root a name.
 */
@TestApplication
class MavenSurefireHistoryOutputTest {
  companion object {
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = true)
  }

  @Test
  fun `the test history keeps the Maven output of a delegated test run`() {
    val history = saveTestHistory { maven -> maven.notifyTextAvailable("[INFO] BUILD SUCCESS\n", ProcessOutputTypes.STDOUT) }.history

    assertTrue(history.contains("name=\"passes\""), history)
    assertTrue(Regex("<root [^>]*>.*BUILD SUCCESS.*</root>", RegexOption.DOT_MATCHES_ALL).containsMatchIn(history), history)
  }

  /**
   * Maven colors its output (`-Dstyle.color=always`), and the process handler of a delegated run gives each colored
   * part of a line its own output type, as [AnsiEscapeDecoder] does here. A run opened from the test history must show
   * the same text as the live run: before the fix, each colored part was on its own line.
   */
  @Test
  fun `a run opened from the test history shows the Maven output as the live run showed it`() {
    val decoder = AnsiEscapeDecoder()
    val run = saveTestHistory { maven ->
      for (line in listOf("[\u001B[1;34mINFO\u001B[m] Scanning for projects...\n",
                          "[\u001B[1;34mINFO\u001B[m] \u001B[1;32mBUILD SUCCESS\u001B[m\n")) {
        decoder.escapeText(line, ProcessOutputTypes.STDOUT) { text, type -> maven.notifyTextAvailable(text, type) }
      }
    }

    // The live run is the reference, so first make sure that it has the two lines.
    assertTrue(run.liveRootText.contains("[INFO] Scanning for projects...\n[INFO] BUILD SUCCESS\n"), run.liveRootText)
    assertEquals(run.liveRootText, openFromTestHistory(run.history), run.history)
  }

  private class SavedRun(val history: String, val liveRootText: String)

  /**
   * Runs a delegated test run whose Maven prints with [printOutput]. Returns the test history entry of the run, and the
   * text that the live run shows on the root node.
   */
  private fun saveTestHistory(printOutput: (FakeMavenProcessHandler) -> Unit): SavedRun {
    val project = project.get()
    val moduleDirectory = tempDir.get().resolve("module")
    val reportsDirectory = moduleDirectory.resolve(SurefireReportParser.SUREFIRE_REPORTS_PATH)
    val configuration = MavenSurefireConfigurationFactory("MyTest", null).createTemplateConfiguration(project) as MavenSurefireRunConfiguration
    configuration.reportSuffix = "42"
    val executor = DefaultRunExecutor.getRunExecutorInstance()
    val maven = FakeMavenProcessHandler()
    val state = SurefireTestRunProfileState(
      RunProfileState { _, _ -> DefaultExecutionResult(maven) },
      configuration,
      executor,
      moduleDirectory.toString(),
    )

    return runInEdtAndGet {
      val result = state.execute(executor, StubRunner)!!
      val console = result.executionConsole as SMTRunnerConsoleView
      try {
        result.processHandler.startNotify()
        printOutput(maven)
        // Surefire writes its report while Maven runs; the delegated run reads it when Maven stops.
        reportsDirectory.createDirectories()
        reportsDirectory.resolve("TEST-probe.MyTest-42.xml").writeText("""
          <testsuite name="probe.MyTest(42)">
            <testcase name="passes" classname="probe.MyTest(42)" time="0.01"/>
          </testsuite>
        """.trimIndent())
        maven.finish()

        // The same writer that saves a test history entry (SMTestRunnerResultsForm).
        val writer = StringWriter()
        val handler = (TransformerFactory.newDefaultInstance() as SAXTransformerFactory).newTransformerHandler()
        handler.setResult(StreamResult(writer))
        val root = console.resultsViewer.testsRootNode
        TestResultsXmlFormatter.execute(root, configuration, console.properties, handler)
        SavedRun(writer.toString(), TextPrinter().also { root.printOwnPrintablesOn(it, false) }.text.toString())
      }
      finally {
        Disposer.dispose(console)
      }
    }
  }

  /** Opens [history] as the Test History action does, and returns the text that the root node of the run shows. */
  private fun openFromTestHistory(history: String): String = runInEdtAndGet {
    val root = SMTestProxy.SMRootTestProxy()
    val processor = GeneralToSMTRunnerEventsConvertor(project.get(), root, "MavenSurefireHistoryOutputTest")
    try {
      processor.onStartTesting()
      ImportedToGeneralTestEventsConverter.parseTestResults({ StringReader(history) }, processor)
      processor.onFinishTesting()
      TextPrinter().also { root.printOwnPrintablesOn(it, false) }.text.toString()
    }
    finally {
      Disposer.dispose(processor)
      Disposer.dispose(root)
    }
  }

  /** Collects the text that a node prints, without the content types. */
  private class TextPrinter : Printer {
    val text = StringBuilder()
    override fun print(text: String, contentType: ConsoleViewContentType) {
      this.text.append(text)
    }

    override fun onNewAvailable(printable: Printable) = printable.printOn(this)
    override fun printHyperlink(text: String, info: HyperlinkInfo?) {
      this.text.append(text)
    }

    override fun mark() {}
  }

  private class FakeMavenProcessHandler : ProcessHandler() {
    fun finish() = notifyProcessTerminated(0)
    override fun destroyProcessImpl() = notifyProcessTerminated(1)
    override fun detachProcessImpl() = notifyProcessDetached()
    override fun detachIsDefault(): Boolean = false
    override fun getProcessInput(): OutputStream? = null
  }

  private object StubRunner : ProgramRunner<RunnerSettings> {
    override fun getRunnerId(): String = "MavenSurefireHistoryOutputTest"
    override fun canRun(executorId: String, profile: RunProfile): Boolean = true
    override fun execute(environment: ExecutionEnvironment) {}
  }
}
