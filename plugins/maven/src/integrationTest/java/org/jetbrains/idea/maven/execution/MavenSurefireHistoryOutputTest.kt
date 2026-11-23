// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.export.TestResultsXmlFormatter
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.runInEdtAndGet
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.io.StringWriter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * IDEA-395942: the test history keeps the output of a test run that the IDE delegates to Maven.
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

    val history = runInEdtAndGet {
      val result = state.execute(executor, StubRunner)!!
      val console = result.executionConsole as SMTRunnerConsoleView
      try {
        result.processHandler.startNotify()
        maven.notifyTextAvailable("[INFO] BUILD SUCCESS\n", ProcessOutputTypes.STDOUT)
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
        TestResultsXmlFormatter.execute(console.resultsViewer.testsRootNode, configuration, console.properties, handler)
        writer.toString()
      }
      finally {
        Disposer.dispose(console)
      }
    }

    assertTrue(history.contains("name=\"passes\""), history)
    assertTrue(Regex("<root [^>]*>.*BUILD SUCCESS.*</root>", RegexOption.DOT_MATCHES_ALL).containsMatchIn(history), history)
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
