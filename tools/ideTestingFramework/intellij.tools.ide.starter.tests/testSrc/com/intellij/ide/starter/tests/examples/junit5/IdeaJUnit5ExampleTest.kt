package com.intellij.ide.starter.tests.examples.junit5

import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.ide.starter.tests.examples.data.TestCases
import com.intellij.metricsCollector.metrics.getOpenTelemetry
import com.jetbrains.performancePlugin.commands.chain.exitApp
import com.jetbrains.performancePlugin.commands.chain.inspectCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JUnit5StarterAssistant::class)
class IdeaJUnit5ExampleTest {

  // these properties will be injected via [JUnit5StarterAssistant]
  private lateinit var testInfo: TestInfo
  private lateinit var context: TestContainerImpl

  @Test
  fun openGradleJitPack() {

    val testContext = context
      .initializeTestContext(testInfo.hyphenateWithClass(), TestCases.IC.GradleJitPackSimple)
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .setSharedIndexesDownload(enable = true)

    val exitCommandChain = CommandChain().exitApp()

    testContext.runIDE(
      commands = exitCommandChain,
      launchName = "first run"
    )

    testContext.runIDE(
      commands = exitCommandChain,
      launchName = "second run"
    )
  }

  @Test
  fun openMavenProject() {

    val testContext = context
      .initializeTestContext(testInfo.hyphenateWithClass(), TestCases.IC.MavenSimpleApp)
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .setSharedIndexesDownload(enable = true)

    testContext.runIDE(commands = CommandChain().exitApp())
  }

  @Test
  fun inspectMavenProject() {
    val testContext = context
      .initializeTestContext(testInfo.hyphenateWithClass(), TestCases.IC.MavenSimpleApp)
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .collectOpenTelemetry()
      .setSharedIndexesDownload(enable = true)

    testContext.runIDE(commands = CommandChain().inspectCode().exitApp())

    getOpenTelemetry(testContext, "globalInspections").metrics.forEach {
      println("Name: " + it.n)
      println("Value: " + it.v + "ms")
    }
  }
}