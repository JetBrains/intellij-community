package com.intellij.ide.starter.tests.examples.junit5

import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.ide.starter.tests.examples.data.TestCases
import com.jetbrains.performancePlugin.commands.chain.exitApp
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JUnit5StarterAssistant::class)
class IdeaJUnit5ExampleTest {

  // these properties will be injected via [JUnit5StarterAssistant]
  private lateinit var testInfo: TestInfo
  private lateinit var context: TestContainerImpl

  @Disabled("Temporary ignore for not running in Ultimate tests suite")
  @Test
  fun openGradleJitPack() {

    val testContext = context
      .initializeTestRunner(testInfo.hyphenateWithClass(), TestCases.IC.GradleJitPackSimple)
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

  @Disabled("Temporary ignore for not running in Ultimate tests suite")
  @Test
  fun openMavenProject() {

    val testContext = context
      .initializeTestRunner(testInfo.hyphenateWithClass(), TestCases.IC.MavenSimpleApp)
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .setSharedIndexesDownload(enable = true)

    testContext.runIDE(commands = CommandChain().exitApp())
  }
}